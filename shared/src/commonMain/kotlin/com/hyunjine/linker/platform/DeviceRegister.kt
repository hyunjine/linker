package com.hyunjine.linker.platform

/**
 * 현재 앱 세션의 device 를 `user_devices` 에 등록하도록 플랫폼에 강제 요청.
 *
 * FCM 토큰 upsert 는 원래 아래 시점에만 fire 된다:
 * - iOS `Messaging.messaging` didReceiveRegistrationToken (토큰이 **새로 발급/rotate** 될 때만)
 * - iOS `scenePhase == .active` 전환 (백/포어그라운드 사이클 필요)
 * - Android [com.hyunjine.linker.push.LinkerFirebaseMessagingService.onNewToken] (동일)
 *
 * 그런데 한 앱 세션 안에서 계정 스왑 (test1 로그아웃 → test2 로그인) 하면 위 셋 중 어느 것도
 * 트리거되지 않아 test2 의 device row 가 생성되지 않는다. 이 함수는 로그인 성공 시점 (session
 * 복원 포함) 에 명시적으로 호출되어 그 gap 을 메꾼다.
 *
 * 세션이 없어도 [com.hyunjine.linker.data.remote.UserDevicesRepository.upsertMyDevice] 가
 * `auth.uid()` 없으면 no-op 이므로 안전. `(user_id, fcm_token)` unique 로 dedupe 되므로 반복
 * 호출도 idempotent.
 *
 * - iOS: Swift 쪽 `PushBridge` 에 등록된 handler 로 위임 (`LinkerPushBridge.ensureFcmTokenRegistered`).
 * - Android: `FirebaseMessaging.getInstance().token` 을 fetch 해 [FcmTokenBridge] 로 upsert.
 */
expect fun ensureCurrentDeviceRegistered()
