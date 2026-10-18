package com.wyrix.bookgenerator

import android.app.Notification
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

    private fun notif(text: String, ongoing: Boolean = true): Notification {
        nm.createNotificationChannel(NotificationChannel("gen", "Book generation", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(applicationContext, "gen")
            .setSmallIcon(if (ongoing) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setContentTitle("WYRIX BOOK GENERATOR AI").setContentText(text).setOngoing(ongoing).build()
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
        } catch (e: NoKeys) {
            nm.notify(2, notif(e.message ?: "API key required", false)); Result.failure()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            nm.notify(2, notif("Paused: ${e.message?.take(60)} - will retry automatically", false)); Result.retry()
        }
    }
}
