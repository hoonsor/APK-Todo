package tw.hoonsor.todo.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import tw.hoonsor.todo.Graph
import tw.hoonsor.todo.R
import tw.hoonsor.todo.ui.MainActivity

/**
 * 提醒：只有 Android APP 會響（網頁不推播）。每次資料變更後整批重排，
 * 用精準鬧鐘（USE_EXACT_ALARM），不行時退回非精準鬧鐘。
 */
object Reminders {
    const val CHANNEL = "reminders"
    const val EXTRA_TASK = "taskId"
    private const val ACTION_FIRE = "tw.hoonsor.todo.REMIND"
    const val ACTION_COMPLETE = "tw.hoonsor.todo.COMPLETE"
    const val ACTION_SNOOZE = "tw.hoonsor.todo.SNOOZE"
    const val SNOOZE_MS = 10 * 60_000L

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "待辦提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "待辦事項設定的提醒時間到了"
                enableVibration(true)
            },
        )
    }

    private fun code(id: String) = id.hashCode()

    private fun firePending(context: Context, id: String) = PendingIntent.getBroadcast(
        context, code(id),
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_TASK, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canExact(context: Context): Boolean = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** 取消舊的、排上所有未完成且在未來的提醒 */
    fun rescheduleAll(context: Context) {
        Graph.scope.launch {
            val am = context.getSystemService(AlarmManager::class.java)
            Graph.settings.scheduledReminders.forEach { am.cancel(firePending(context, it)) }
            val now = System.currentTimeMillis()
            val due = if (Graph.api.signedIn) Graph.repo.dao.tasks().filter { !it.done && (it.remindAt ?: 0) > now } else emptyList()
            due.forEach { t ->
                val pi = firePending(context, t.id)
                if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.remindAt!!, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.remindAt!!, pi)
            }
            Graph.settings.scheduledReminders = due.map { it.id }.toSet()
        }
    }

    fun show(context: Context, id: String, title: String, sub: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context, code(id), Intent(context, MainActivity::class.java).putExtra(EXTRA_TASK, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(a: String) = PendingIntent.getBroadcast(
            context, code(id) + a.hashCode(), Intent(context, ReminderReceiver::class.java).setAction(a).putExtra(EXTRA_TASK, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(sub)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "完成", action(ACTION_COMPLETE))
            .addAction(0, "10 分鐘後再提醒", action(ACTION_SNOOZE))
            .build()
        NotificationManagerCompat.from(context).notify(code(id), n)
    }

    fun dismiss(context: Context, id: String) = NotificationManagerCompat.from(context).cancel(code(id))
}

/** 提醒時間到、通知上的「完成」「延後」 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Reminders.EXTRA_TASK) ?: return
        val pending = goAsync()
        Graph.scope.launch {
            try {
                when (intent.action) {
                    Reminders.ACTION_COMPLETE -> {
                        Graph.repo.setCompleted(id, true)
                        Reminders.dismiss(context, id)
                    }
                    Reminders.ACTION_SNOOZE -> {
                        Graph.repo.updateTask(id) { it.copy(remindAt = System.currentTimeMillis() + Reminders.SNOOZE_MS) }
                        Reminders.dismiss(context, id)
                    }
                    else -> {
                        val t = Graph.repo.dao.task(id)
                        if (t != null && !t.done && t.deletedAt == null) {
                            val list = Graph.repo.dao.list(t.listId)?.name.orEmpty()
                            Reminders.show(context, id, t.title, listOf(list, t.dueDate?.let { "到期 $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "))
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** 開機或更新 APP 後重排提醒 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.rescheduleAll(context)
    }
}
