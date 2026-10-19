package `in`.senthilnathan_dev.sentrack

import android.app.Application
import `in`.senthilnathan_dev.sentrack.security.AppContainer
import `in`.senthilnathan_dev.sentrack.security.AutoLocker

/** Application class: owns the process-wide [AppContainer] and installs auto-lock. */
class SenTrackApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AutoLocker(this, container).install()
        // Make sure a next scheduled backup is queued (KEEP: never disturbs one that is already waiting or running).
        container.backupScheduler.reschedule(androidx.work.ExistingWorkPolicy.KEEP)
    }
}
