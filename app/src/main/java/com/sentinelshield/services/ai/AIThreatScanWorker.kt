package com.sentinelshield.services.ai

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically runs the AI threat engine
 * to scan all apps for behavioral anomalies.
 */
class AIThreatScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AIThreatScanWorker"
        private const val WORK_NAME = "ai_threat_scan"
        private const val REPEAT_INTERVAL_HOURS = 8L

        /**
         * Schedule periodic AI threat scans.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AIThreatScanWorker>(
                REPEAT_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES)  // Don't run immediately on app start
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.i(TAG, "AI threat scan scheduled every $REPEAT_INTERVAL_HOURS hours")
        }

        /**
         * Run an immediate one-time AI scan.
         */
        fun runNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<AIThreatScanWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.i(TAG, "Immediate AI threat scan triggered")
        }

        /**
         * Cancel scheduled AI scans.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "AI threat scan cancelled")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        Log.i(TAG, "Starting AI threat scan worker...")

        return@withContext try {
            val engine = AIThreatEngine(applicationContext)
            val threats = engine.runFullScan()

            Log.i(TAG, "AI scan complete: ${threats.size} threats found")

            // Store results summary in shared preferences for UI access
            val prefs = applicationContext.getSharedPreferences("ai_scan_results", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt("last_scan_threats", threats.size)
                putLong("last_scan_time", System.currentTimeMillis())
                putInt("total_apps_scanned", engine.getStats().totalAppsMonitored)

                // Store top threats
                val topThreats = threats.take(5).joinToString("|") { 
                    "${it.appName}:${it.classification.label}:${it.classification.confidence}" 
                }
                putString("top_threats", topThreats)
                apply()
            }

            engine.shutdown()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "AI threat scan failed: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
