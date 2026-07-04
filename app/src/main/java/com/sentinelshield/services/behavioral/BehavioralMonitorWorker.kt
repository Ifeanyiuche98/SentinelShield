package com.sentinelshield.services.behavioral

import android.content.Context
import android.util.Log
import androidx.work.*
import com.sentinelshield.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based periodic behavioral analysis.
 * Runs behavioral checks on all apps and alerts on anomalies.
 */
class BehavioralMonitorWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BehavioralMonitorWorker"
        private const val WORK_NAME = "behavioral_monitor"
        private const val MONITOR_INTERVAL_HOURS = 4L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<BehavioralMonitorWorker>(
                MONITOR_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.i(TAG, "Behavioral monitor scheduled (every ${MONITOR_INTERVAL_HOURS}h)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Behavioral monitor cancelled")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Running behavioral analysis...")
            val analyzer = BehavioralAnalyzer(applicationContext)
            val anomalies = analyzer.analyzeAllApps()

            if (anomalies.isNotEmpty()) {
                val notificationHelper = NotificationHelper(applicationContext)
                // Only notify for high-severity anomalies
                anomalies.filter { it.severity >= 0.6f }.take(3).forEach { anomaly ->
                    notificationHelper.showBehavioralAnomaly(anomaly)
                }
                Log.i(TAG, "Found ${anomalies.size} anomalies, notified on ${anomalies.count { it.severity >= 0.6f }}")
            } else {
                Log.i(TAG, "No behavioral anomalies detected")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Behavioral analysis failed", e)
            Result.retry()
        }
    }
}
