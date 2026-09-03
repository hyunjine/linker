package com.hyunjine.linker.platform

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService

/** 실제 알람 fire 를 받아 시스템 알림을 뜨우는 브로드캐스트 액션. androidApp receiver 가 리스닝. */
const val REMINDER_BROADCAST_ACTION = "com.hyunjine.linker.REMINDER_FIRE"

/** 알람 broadcast intent extras 키. Receiver 가 알림 build 에 쓴다. */
const val REMINDER_EXTRA_ID = "reminder_id"
const val REMINDER_EXTRA_TITLE = "reminder_title"
const val REMINDER_EXTRA_BODY = "reminder_body"

/** 스케줄 알림 채널 id — Receiver 도 같은 걸 쓴다. */
const val REMINDER_CHANNEL_ID = "schedule_reminder"

actual object LocalNotifications {
    private var app: Context? = null

    /**
     * `LinkerApplication.onCreate` 에서 앱 컨텍스트로 한 번 호출.
     * NotificationChannel 도 여기서 생성. 이미 있으면 no-op.
     */
    fun init(context: Context) {
        val ctx = context.applicationContext
        app = ctx

        // Notification channel: 오레오+ 필수. importance HIGH 는 heads-up 을 허용.
        val nm = ctx.getSystemService<NotificationManager>() ?: return
        val existing = nm.getNotificationChannel(REMINDER_CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "일정 알림",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "저장한 일정의 시작 시각에 뜨는 알림"
            }
            nm.createNotificationChannel(channel)
        }
    }

    actual fun schedule(id: String, title: String, body: String, epochSeconds: Long) {
        val ctx = app ?: return
        val nowMs = System.currentTimeMillis()
        val triggerMs = epochSeconds * 1000L
        if (triggerMs <= nowMs) return   // past — skip
        val am = ctx.getSystemService<AlarmManager>() ?: return
        val pi = pendingIntent(ctx, id, title, body, mutable = true)
        // 정확한 시각에 fire 되도록 setExactAndAllowWhileIdle. Doze 모드에서도 늦어도 몇 분 안에는 fire.
        // 안드로이드 12+ 는 SCHEDULE_EXACT_ALARM 권한이 필요 — 우리 앱은 이 권한을 declare 만 하고 사용자가
        // 별도로 켜지 않아도 되는 alarm-clock 스타일 대체로 setAndAllowWhileIdle 을 씀 (정밀도 살짝 낮음).
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
    }

    actual fun cancel(id: String) {
        val ctx = app ?: return
        val am = ctx.getSystemService<AlarmManager>() ?: return
        val pi = pendingIntent(ctx, id, title = "", body = "", mutable = false)
        am.cancel(pi)
        pi.cancel()
    }

    actual fun cancelAll() {
        // AlarmManager 는 앱-전역 cancelAll 이 없어 개별 pending intent 를 요구. shared 에서 id 목록을
        // 알고 있어야 함 — 상위 ReminderScheduler 가 이전 예약 id 세트를 관리하며 하나씩 [cancel] 호출.
        // 이 함수는 방어적으로 no-op (실제 취소는 상위 책임).
    }

    private fun pendingIntent(ctx: Context, id: String, title: String, body: String, mutable: Boolean): PendingIntent {
        val intent = Intent(REMINDER_BROADCAST_ACTION).apply {
            setPackage(ctx.packageName)
            putExtra(REMINDER_EXTRA_ID, id)
            putExtra(REMINDER_EXTRA_TITLE, title)
            putExtra(REMINDER_EXTRA_BODY, body)
        }
        val flags = if (mutable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_NO_CREATE
        }
        // requestCode 로 id 해시 사용 — 서로 다른 스케줄이면 다른 PendingIntent.
        return PendingIntent.getBroadcast(ctx, id.hashCode(), intent, flags)
    }
}
