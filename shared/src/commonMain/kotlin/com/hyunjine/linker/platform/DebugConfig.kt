package com.hyunjine.linker.platform

/**
 * 앱이 debug 빌드로 돌고 있는지 여부. Debug 전용 UI · 로그를 게이팅하는 데 쓴다.
 *
 * 초기값은 false (Release). 각 플랫폼 진입점에서 debug 판정 후 [enabled] 를 set 해야 한다.
 * - Android: `LinkerApplication.onCreate` 에서 `applicationInfo.flags and FLAG_DEBUGGABLE != 0` 로 세팅.
 * - iOS: `iOSApp.init` 에서 `#if DEBUG` 로 true 세팅.
 *
 * commonMain 코드에서 `if (DebugConfig.enabled)` 로 UI 노출 여부 결정.
 */
object DebugConfig {
    var enabled: Boolean = false
}
