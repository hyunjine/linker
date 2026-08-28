package com.hyunjine.linker.platform

/** Swift 가 [WidgetBridge.handler] 를 등록해 두면 그리로 위임. 미등록이면 조용히 무시. */
actual fun refreshTodayWidget() {
    WidgetBridge.handler?.invoke()
}
