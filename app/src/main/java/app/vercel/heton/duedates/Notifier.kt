package app.vercel.heton.duedates

import android.app.Notification
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
import java.util.concurrent.TimeUnit

object Notifier {
    private const val CHANNEL = "reminders_v2"
    private const val GROUP = "pending"
    private const val SUMMARY_ID = 1
    private const val MAX_INDIVIDUAL = 3

    fun schedule(context: Context, slot: Int) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val s = Prefs.load(context)
        var next = now.toLocalDate().atTime(s.hour, s.minute).atZone(zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = OneTimeWorkRequestBuilder<NotifyWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("slot" to slot))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("notify_$slot", ExistingWorkPolicy.REPLACE, request)
    }

    fun restart(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("notify_1")
        schedule(context, 0)
    }

    private fun manager(context: Context): NotificationManager {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("reminders")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        return nm
    }

    private fun openIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun post(context: Context, nm: NotificationManager, o: Occ, today: LocalDate, grouped: Boolean) {
        val nid = o.key.hashCode()
        val done = Intent(context, MarkDoneReceiver::class.java)
            .putExtra("g", o.groupId)
            .putExtra("p", o.period)
            .putExtra("t", o.title)
            .putExtra("n", o.note)
            .putExtra("d", o.dueDate.toString())
            .putExtra("i", o.icon)
            .putExtra("c", o.color)
            .putExtra("w", o.notifyWeekends)
            .putExtra("nid", nid)
        val donePi = PendingIntent.getBroadcast(
            context,
            nid,
            done,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (o.dueDate.isBefore(today)) {
            context.getString(R.string.overdue_since, o.dueDate.format(dateFmt("MMMd")))
        } else {
            context.getString(R.string.due_today_notif)
        }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(o.title)
            .setContentText(text)
            .setContentIntent(openIntent(context))
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.mark_done), donePi)
        if (grouped) b.setGroup(GROUP).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        nm.notify(nid, b.build())
    }

    private fun summary(context: Context, titles: List<String>): Notification {
        val inbox = NotificationCompat.InboxStyle()
        titles.take(6).forEach { inbox.addLine(it) }
        if (titles.size > 6) inbox.setSummaryText(context.getString(R.string.more_summary, titles.size - 6))
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.resources.plural(R.plurals.pending_reminders, titles.size))
            .setContentText(context.getString(R.string.tap_review))
            .setStyle(inbox)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent(context))
            .setAutoCancel(true)
            .build()
    }

    fun showAll(base: Context, list: List<Occ>, today: LocalDate) {
        val context = localized(base)
        val nm = manager(context)
        if (list.size > MAX_INDIVIDUAL) {
            list.forEach { post(context, nm, it, today, true) }
            nm.notify(SUMMARY_ID, summary(context, list.map { it.title }))
        } else {
            nm.cancel(SUMMARY_ID)
            list.forEach { post(context, nm, it, today, false) }
        }
    }

    fun refreshSummary(base: Context) {
        val context = localized(base)
        val nm = context.getSystemService(NotificationManager::class.java)
        val active = nm.activeNotifications
        if (active.none { it.id == SUMMARY_ID }) return
        val children = active.filter { it.id != SUMMARY_ID && it.notification.group == GROUP }
        if (children.isEmpty()) {
            nm.cancel(SUMMARY_ID)
            return
        }
        val titles = children.map { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "" }
        nm.notify(SUMMARY_ID, summary(context, titles))
    }
}

class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = Db.get(applicationContext).dao()
        val today = LocalDate.now()
        val weekend = isWeekend(today)
        val list = pendingDue(dao.remindersNow(), dao.statesNow(), today).filter { !weekend || it.notifyWeekends }
        Notifier.showAll(applicationContext, list, today)
        DueWidgetUpdater.update(applicationContext)
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
                    adjusted = false,
                    icon = intent.getStringExtra("i") ?: "bill",
                    color = intent.getIntExtra("c", 0),
                    notifyWeekends = intent.getBooleanExtra("w", true)
                )
                Repo(Db.get(context).dao()).complete(o, true)
                context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("nid", 0))
                Notifier.refreshSummary(context)
                DueWidgetUpdater.update(context)
            } finally {
                pending.finish()
            }
        }
    }
}
