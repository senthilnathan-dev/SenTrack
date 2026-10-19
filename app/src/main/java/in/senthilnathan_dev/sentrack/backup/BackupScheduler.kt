package `in`.senthilnathan_dev.sentrack.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import `in`.senthilnathan_dev.sentrack.SenTrackApp
import `in`.senthilnathan_dev.sentrack.core.sync.CronSchedule
import `in`.senthilnathan_dev.sentrack.core.sync.NetworkPolicy
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Turns the user's cron schedule into Android background work.
 *
 * WorkManager has no cron support, so exactly one delayed job is queued for the next due time; when it finishes
 * it queues the next one. The job carries Android constraints (network type, charging), so it waits until they are
 * met. Android may start it a few minutes late (battery saving), never early.
 */
class BackupScheduler(private val context: Context, private val settings: BackupSettings) {

    /**
     * (Re)queues the next backup, or cancels everything if automatic backups are off or not possible.
     * Use REPLACE when the user changed a setting, KEEP at app start, APPEND_OR_REPLACE from the worker itself.
     */
    fun reschedule(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val work = WorkManager.getInstance(context)
        val cron = CronSchedule.parseOrNull(settings.cron)
        if (!settings.scheduleEnabled || !settings.connected || cron == null) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = ZonedDateTime.now()
        val delay = Duration.between(now, cron.nextAfter(now))
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                when (settings.network) {
                    NetworkPolicy.WIFI_ONLY -> NetworkType.UNMETERED
                    NetworkPolicy.MOBILE_ONLY -> NetworkType.METERED
                    NetworkPolicy.ANY -> NetworkType.CONNECTED
                },
            )
            .setRequiresCharging(settings.requireCharging)
            .build()
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        work.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "sentrack-scheduled-backup"
    }
}

/** Runs one scheduled backup, then queues the next one. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SenTrackApp).container
        val settings = container.backupSettings
        if (!settings.scheduleEnabled || !settings.connected) return Result.success()

        val outcome = container.backupManager.backupNow()
        // A transient failure (e.g. flaky network) is retried a couple of times with back-off before giving up
        // until the next scheduled time. The result is always visible on the Backup screen.
        if (outcome is BackupOutcome.Failure && runAttemptCount < 2) return Result.retry()

        container.backupScheduler.reschedule(ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
