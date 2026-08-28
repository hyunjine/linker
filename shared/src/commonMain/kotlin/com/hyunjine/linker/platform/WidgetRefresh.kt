package com.hyunjine.linker.platform

/**
 * 오늘 일정 위젯을 최신 payload 로 갱신하도록 플랫폼에 요청.
 * 스케줄 CRUD 성공 직후에 호출 — 앱이 foreground 에 있어 [iOSApp.scenePhase] 가 안 바뀌는 케이스를 커버.
 *
 * - iOS: Swift 쪽 `WidgetBridge` 에 등록된 handler 로 위임 (shared JSON build → App Group write → WidgetKit reload).
 * - Android: 위젯이 없으므로 no-op.
 */
expect fun refreshTodayWidget()
