package app.duely

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

object Notifier {
    private const val CHANNEL = "reminders"
    private val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun schedule(context: Context, slot: Int) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val next = now.toLocalDate().plusDays(1).atStartOfDay(zone)
        val request = OneTimeWorkRequestBuilder<NotifyWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("slot" to slot))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("notify_$slot", ExistingWorkPolicy.REPLACE, request)
    }

    fun runTest(context: Context) {
        val request = OneTimeWorkRequestBuilder<NotifyWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setInputData(workDataOf("slot" to -1))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun restart(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("notify_1")
        schedule(context, 0)
    }

    fun show(context: Context, o: Occ, today: LocalDate) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        val nid = o.key.hashCode()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val done = Intent(context, MarkDoneReceiver::class.java)
            .putExtra("g", o.groupId)
            .putExtra("p", o.period)
            .putExtra("t", o.title)
            .putExtra("n", o.note)
            .putExtra("d", o.dueDate.toString())
            .putExtra("nid", nid)
        val donePi = PendingIntent.getBroadcast(context, nid, done, flags)
        val openPi = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
        val text = if (o.dueDate.isBefore(today)) "Overdue since ${o.dueDate.format(fmt)}" else "Due today"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(o.title)
            .setContentText(text)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .addAction(0, "Mark as done", donePi)
            .build()
        nm.notify(nid, notification)
    }
}

class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = Db.get(applicationContext).dao()
        val today = LocalDate.now()
        pendingDue(dao.remindersNow(), dao.statesNow(), today).forEach { Notifier.show(applicationContext, it, today) }
        val slot = inputData.getInt("slot", 0)
        if (slot >= 0) Notifier.schedule(applicationContext, 1 - slot)
        return Result.success()
    }
}

class MarkDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val o = Occ(
                    groupId = intent.getStringExtra("g") ?: return@launch,
                    period = intent.getStringExtra("p") ?: return@launch,
                    title = intent.getStringExtra("t") ?: "",
                    note = intent.getStringExtra("n"),
                    dueDate = LocalDate.parse(intent.getStringExtra("d")),
                    completedAt = null,
                    isException = false,
                    once = false,
                    adjusted = false
                )
                Repo(Db.get(context).dao()).complete(o, true)
                context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("nid", 0))
            } finally {
                pending.finish()
            }
        }
    }
}
