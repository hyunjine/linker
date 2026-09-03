package com.hyunjine.linker.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.hyunjine.linker.MainActivity
import com.hyunjine.linker.R
import com.hyunjine.linker.platform.REMINDER_CHANNEL_ID
import com.hyunjine.linker.platform.REMINDER_EXTRA_BODY
import com.hyunjine.linker.platform.REMINDER_EXTRA_ID
import com.hyunjine.linker.platform.REMINDER_EXTRA_TITLE

/**
 * AlarmManager 가 예약된 시각에 broadcast 를 쏘면 여기서 받아 시스템 알림을 띄운다.
 * intent action 은 [com.hyunjine.linker.platform.REMINDER_BROADCAST_ACTION] — LocalNotifications 가 세팅.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(REMINDER_EXTRA_ID) ?: return
        val title = intent.getStringExtra(REMINDER_EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(REMINDER_EXTRA_BODY).orEmpty()

        // 알림 탭 → MainActivity 열기. singleTask 라 이미 떠있으면 그 화면 위로.
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context, id.hashCode(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { "일정 알림" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.notify(id.hashCode(), notification)
    }
}
