package com.jondude1.orbit.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jondude1.orbit.R
import com.jondude1.orbit.data.Orbit
import com.jondude1.orbit.data.Settings
import com.jondude1.orbit.data.ThoughtStore
import com.jondude1.orbit.ui.MainActivity
import java.util.concurrent.TimeUnit

object ReviewScheduler {
    const val CHANNEL = "orbit_review"
    private const val WORK = "orbit-review"
    private const val NOTIF_ID = 42

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ideas coming back around", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your daily nudge when thoughts swing back into view"
            }
        )
    }

    /** Queue the next nudge for the next review slot. Safe to call often. */
    fun schedule(context: Context) {
        val now = System.currentTimeMillis()
        val next = Orbit.slotAtOrAfter(now + 60_000)
        val delay = (next - now).coerceAtLeast(60_000)
        val req = OneTimeWorkRequestBuilder<ReviewWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Post the nudge. Returns false if there was nothing to say or no permission. */
    fun notifyIfDue(context: Context, force: Boolean = false): Boolean {
        if (!canNotify(context)) return false
        if (!force && !Settings.current.notifications) return false
        val due = ThoughtStore.due()
        if (due.isEmpty() && !force) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("dest", "review")
        }
        val pi = PendingIntent.getActivity(
            context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = when (due.size) {
            0 -> "Orbit is set up"
            1 -> "A thought swung back around"
            else -> "${due.size} thoughts swung back around"
        }
        val preview = due.take(3).joinToString("\n") { "• " + it.title.take(80) }
        val text = if (due.isEmpty()) "This is what your nudge will look like." else preview

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_orbit)
            .setContentTitle(title)
            .setContentText(due.firstOrNull()?.title ?: text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, n)
        } catch (_: SecurityException) {
            return false
        }
        return true
    }
}

class ReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ReviewScheduler.notifyIfDue(applicationContext)
        ReviewScheduler.schedule(applicationContext)
        return Result.success()
    }
}
