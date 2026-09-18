package com.hyunjine.linker.platform

/**
 * Swift 쪽 브리지. `iOSApp.init` 에서 [handler] 에 `LinkerPushBridge.shared.ensureFcmTokenRegistered`
 * 를 꽂아주면 shared 가 이를 통해 iOS 네이티브의 토큰 fetch · upsert 흐름을 트리거한다.
 *
 * [WidgetBridge] 와 동일 패턴 — Swift 는 KMP `expect fun` 을 직접 override 못하므로 handler
 * property 를 노출해 우회.
 */
object PushBridge {
    /** Swift 가 세팅. 미등록 상태에서 호출돼도 [ensureCurrentDeviceRegistered] 가 조용히 무시. */
    var handler: (() -> Unit)? = null
}

/** Swift 가 [PushBridge.handler] 를 등록해 두면 그리로 위임. 미등록이면 조용히 무시. */
actual fun ensureCurrentDeviceRegistered() {
    PushBridge.handler?.invoke()
}
