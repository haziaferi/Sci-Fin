package app.scifin

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.scifin.app.AndroidCopyPlace
import app.scifin.app.AppSession
import app.scifin.data.buildSciFinDatabase
import app.scifin.engine.SystemClock
import app.scifin.engine.newId
import app.scifin.notify.AndroidNotificationScheduler
import java.util.concurrent.TimeUnit

/**
 * One database, scheduler and session per process, shared by the Activity and the workers: built
 * per Activity, a recreation would open a second Room instance on the same file and re-run §3.9's
 * catch-up. [db] is first read off the main thread ([StoreViewModel]); building it runs §12's checks.
 */
class SciFinApplication : Application() {
    val scheduler by lazy { AndroidNotificationScheduler(applicationContext) }
    val db by lazy { buildSciFinDatabase(applicationContext) }
    val session by lazy { AppSession(db, scheduler, SystemClock, ::newId, AndroidCopyPlace(applicationContext)) }

    override fun onCreate() {
        super.onCreate()
        // §11.5's daily top-up (Windows: the scheduled task): re-arms reminders while the app goes unopened.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TOP_UP_WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TopUpWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    companion object {
        const val TOP_UP_WORK = "scifin.topUp"
    }
}

/** Re-arms the whole schedule from state as it stands today, with no Activity alive, and writes §4.7's automatic copy when due. */
class TopUpWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        (applicationContext as SciFinApplication).session.restoreAfterBoot()
        return Result.success()
    }
}
