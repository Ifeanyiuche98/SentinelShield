package com.sentinelshield.services.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.sentinelshield.R
import com.sentinelshield.data.database.ThreatDatabase
import com.sentinelshield.data.models.NetworkConnection
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * VPN-based network traffic monitor that intercepts and inspects
 * all network traffic from the device without requiring root access.
 * Flags connections to known malicious IPs and suspicious domains.
 */
class NetworkMonitorService : VpnService() {

    companion object {
        const val CHANNEL_ID = "network_monitor_channel"
        const val NOTIFICATION_ID = 3001
        var isRunning = false
            private set

        // Tracked connections
        val activeConnections = mutableListOf<NetworkConnection>()
        val suspiciousConnections = mutableListOf<NetworkConnection>()

        fun getConnectionCount(): Int = activeConnections.size
        fun getSuspiciousCount(): Int = suspiciousConnections.size
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var monitorThread: Thread? = null
    private lateinit var threatDb: ThreatDatabase

    override fun onCreate() {
        super.onCreate()
        threatDb = ThreatDatabase(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    /**
     * Start the VPN and begin monitoring traffic.
     */
    private fun startVpn() {
        if (isRunning) return

        try {
            val builder = Builder()
                .setSession("SentinelShield Network Monitor")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(1500)

            vpnInterface = builder.establish()
            isRunning = true

            // Start monitoring in background thread
            monitorThread = Thread { monitorTraffic() }
            monitorThread?.start()

            // Show persistent notification
            showNotification()

        } catch (e: Exception) {
            isRunning = false
        }
    }

    /**
     * Monitor network traffic passing through the VPN tunnel.
     */
    private fun monitorTraffic() {
        val vpnFd = vpnInterface ?: return
        val input = FileInputStream(vpnFd.fileDescriptor)
        val output = FileOutputStream(vpnFd.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767)

        while (isRunning) {
            try {
                val length = input.read(buffer.array())
                if (length > 0) {
                    buffer.limit(length)
                    processPacket(buffer)
                    buffer.clear()

                    // Forward the packet (pass-through mode)
                    output.write(buffer.array(), 0, length)
                }
            } catch (e: Exception) {
                if (!isRunning) break
            }
        }
    }

    /**
     * Process an IP packet and extract connection information.
     */
    private fun processPacket(packet: ByteBuffer) {
        try {
            if (packet.limit() < 20) return // Too short for IP header

            val version = (packet.get(0).toInt() shr 4) and 0xF
            if (version != 4) return // Only handle IPv4 for now

            val protocol = packet.get(9).toInt() and 0xFF
            val protocolName = when (protocol) {
                6 -> "TCP"
                17 -> "UDP"
                else -> "OTHER"
            }

            // Extract destination IP
            val destIpBytes = ByteArray(4)
            packet.position(16)
            packet.get(destIpBytes)
            val destIp = InetAddress.getByAddress(destIpBytes).hostAddress ?: return

            // Extract destination port (for TCP/UDP)
            var destPort = 0
            if (protocol == 6 || protocol == 17) {
                val headerLength = (packet.get(0).toInt() and 0xF) * 4
                if (packet.limit() > headerLength + 3) {
                    destPort = ((packet.get(headerLength + 2).toInt() and 0xFF) shl 8) or
                            (packet.get(headerLength + 3).toInt() and 0xFF)
                }
            }

            // Check against threat database
            val (isSuspicious, reason) = threatDb.isSuspiciousIp(destIp)

            val connection = NetworkConnection(
                sourceApp = "Unknown", // Would need proc/net parsing for app attribution
                destinationIp = destIp,
                destinationDomain = tryReverseDns(destIp),
                port = destPort,
                protocol = protocolName,
                isSuspicious = isSuspicious
            )

            // Track connection
            synchronized(activeConnections) {
                activeConnections.add(connection)
                // Keep only last 1000 connections
                if (activeConnections.size > 1000) {
                    activeConnections.removeAt(0)
                }
            }

            if (isSuspicious) {
                synchronized(suspiciousConnections) {
                    suspiciousConnections.add(connection)
                }
                notifySuspiciousConnection(destIp, reason)
            }

        } catch (e: Exception) {
            // Malformed packet, skip
        }
    }

    /**
     * Attempt reverse DNS lookup for an IP.
     */
    private fun tryReverseDns(ip: String): String {
        return try {
            InetAddress.getByName(ip).canonicalHostName
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Notify user of suspicious connection.
     */
    private fun notifySuspiciousConnection(ip: String, reason: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("⚠️ Suspicious Connection")
            .setContentText("Connection to $ip detected. Reason: $reason")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(
            NOTIFICATION_ID + suspiciousConnections.size,
            notification
        )
    }

    private fun showNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("SentinelShield Network Monitor")
            .setContentText("Monitoring network traffic...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopVpn() {
        isRunning = false
        monitorThread?.interrupt()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Network traffic monitoring notifications"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
