package com.hyunjine.linker.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hyunjine.linker.MainActivity
import com.hyunjine.linker.R
import com.hyunjine.linker.data.remote.OutlookBackgroundSyncAndroid
import com.hyunjine.linker.platform.FcmTokenBridge

/**
 * FCM 토큰 refresh 수신 + push 메시지 처리.
 *
 * - onNewToken: Firebase 가 새 토큰을 발급하면 호출. shared 의 [FcmTokenBridge] 로 통보해서
 *   Supabase user_devices 에 upsert 하도록 위임.
 * - onMessageReceived: foreground 상태에서 push 도착. 시스템 알림으로 노출.
 *   단 `data.type = "outlook_sync"` (#420) 는 알림 없이 Outlook 재택 백그라운드 sync 만 실행.
 *   (백그라운드 · terminated 는 Firebase 가 자동으로 트레이 알림 표시.)
 */
class LinkerFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken: ${token.take(12)}…")
        FcmTokenBridge.onTokenRefreshedAsync(token, platform = "android")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // 서버 30분 주기 Outlook 재택 sync 깨우기 (#420). data-only 라 알림을 띄우지 않고 sync 만 돌린다.
        // onMessageReceived 는 작업 스레드에서 불리므로 여기서 막고 끝까지 실행 (OutlookBackgroundSyncAndroid 참고).
        if (message.data[KEY_TYPE] == TYPE_OUTLOOK_SYNC) {
            val outcome = OutlookBackgroundSyncAndroid.runSync(this)
            Log.d(TAG, "outlook_sync 완료: $outcome")
            return
        }
        val title = message.notification?.title ?: message.data["title"] ?: "새 알림"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        ensureChannel()
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "일정 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "파트너가 만든 스케줄 · 기념일 알림"
            },
        )
    }

    companion object {
        private const val TAG = "LinkerFCM"
        private const val CHANNEL_ID = "schedule_notifications"

        /** 서버 data push 의 종류 키 (#420). */
        private const val KEY_TYPE = "type"

        /** Outlook 재택 sync 깨우기 push (`send-outlook-sync-push`). */
        private const val TYPE_OUTLOOK_SYNC = "outlook_sync"
    }
}
