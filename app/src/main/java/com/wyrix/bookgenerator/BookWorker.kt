package com.wyrix.bookgenerator

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class BookWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    private fun open(): PendingIntent = PendingIntent.getActivity(
        applicationContext, 0, Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notif(text: String, ongoing: Boolean = true): Notification {
        nm.createNotificationChannel(NotificationChannel("gen", "Book generation", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(applicationContext, "gen")
            .setSmallIcon(R.drawable.ic_stat_wyrix)
            .setContentTitle("WYRIX - AI Book Generator").setContentIntent(open()).setContentText(text).setOngoing(ongoing).build()
    }

    override suspend fun getForegroundInfo() =
        ForegroundInfo(1, notif("Starting..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    override suspend fun doWork(): Result {
        val id = inputData.getLong("id", 0)
        try { setForeground(getForegroundInfo()) } catch (e: Exception) { /* background start: ab bhi chalega */ }
        return try {
            Engine.run(applicationContext, id) { nm.notify(1, notif(it)) }
            nm.notify(2, notif("Your book is ready! Open the app to export PDF/EPUB", false))
            Result.success()
        } catch (e: ContentBlocked) {
            Db.get(applicationContext).dao().let { d -> d.book(id)?.let { d.saveBook(it.copy(status = "BLOCKED")) } }
            nm.notify(2, notif("Stopped: this book request contains content that is not allowed.", false)); Result.failure()
        } catch (e: NoKeys) {
            nm.notify(2, notif(e.message ?: "API key required", false)); Result.failure()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            nm.notify(2, notif("Paused: ${e.message?.take(60)} - will retry automatically", false)); Result.retry()
        }
    }
}
