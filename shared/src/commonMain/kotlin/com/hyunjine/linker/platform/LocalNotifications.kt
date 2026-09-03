package com.hyunjine.linker.platform

/**
 * OS 예약 알림 스케줄러. 스케줄 시작 시각에 로컬 알림을 띄우기 위한 얇은 플랫폼 래퍼.
 *
 * "로컬" 이라는 이유: FCM 은 즉시 발송 프로토콜이라 시각-도래-시 자동 발화는 서버-사이드
 * cron/스케줄러가 필요. 그 인프라 도입 전엔 각 기기 OS 스케줄러 (AlarmManager · UN)
 * 로 예약해두는 게 오프라인 안전 · 지연 최소.
 *
 * Android 는 [init] 을 LinkerApplication.onCreate 에서 반드시 한 번 호출해야 함
 * (Notification channel · Application context 필요). iOS 는 unneeded.
 *
 * 상한:
 *  - iOS 는 앱당 pending notification 이 64 개 제한. 상위 [ReminderScheduler] 가 상한 안에서 관리.
 *  - Android 는 실제 상한이 없지만 [ReminderScheduler] 는 성능·간결성 위해 동일 규칙 적용.
 */
expect object LocalNotifications {
    /**
     * [id] 를 키로 [epochSeconds] 절대 시각에 알림 예약. 같은 id 로 재호출하면 덮어씀 (idempotent).
     * 과거 시각을 넘기면 무시 (플랫폼별로 즉시 발화하거나 무시하는데, 일관성을 위해 우리도 skip).
     */
    fun schedule(id: String, title: String, body: String, epochSeconds: Long)

    /** [id] 의 예약을 취소. 없어도 no-op. */
    fun cancel(id: String)

    /** 앱이 발급한 모든 예약 취소. 재-scheduling 시 대량 취소 편의용. */
    fun cancelAll()
}
