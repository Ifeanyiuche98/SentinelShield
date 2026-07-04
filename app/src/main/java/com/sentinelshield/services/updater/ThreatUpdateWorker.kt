package com.sentinelshield.services.updater

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based periodic threat database updater.
 * Runs in the background even when the app is closed.
 */
class ThreatUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ThreatUpdateWorker"
        private const val WORK_NAME = "threat_feed_update"

        /**
         * Schedule periodic threat feed updates using WorkManager
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ThreatUpdateWorker>(
                ThreatFeedUpdater.UPDATE_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.i(TAG, "Threat feed update worker scheduled")
        }

        /**
         * Cancel scheduled updates
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Threat feed update worker cancelled")
        }

        /**
         * Trigger an immediate one-time update
         */
        fun triggerNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ThreatUpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.i(TAG, "Immediate threat feed update triggered")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting threat feed update work...")
            val updater = ThreatFeedUpdater(applicationContext)
            val result = updater.performUpdate()

            if (result.errors.isEmpty()) {
                Log.i(TAG, "Update successful: ${result.malwareHashesAdded} hashes, ${result.phishingUrlsAdded} URLs, ${result.maliciousIpsAdded} IPs")
                Result.success()
            } else if (result.malwareHashesAdded > 0 || result.phishingUrlsAdded > 0 || result.maliciousIpsAdded > 0) {
                // Partial success
                Log.w(TAG, "Partial update: ${result.errors.size} errors")
                Result.success()
            } else {
                Log.e(TAG, "Update failed: ${result.errors}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.retry()
        }
    }
}
