package com.hyunjine.linker.platform

/**
 * iOS Kotlin/Native ↔ Swift 브리지. shared 는 위젯 refresh 요청만 던지고, 실제 App Group 파일 write ·
 * `WidgetCenter.reloadAllTimelines()` 호출은 Swift 쪽 (`WidgetSync.refresh`) 가 담당.
 *
 * 사용 (iOSApp.swift):
 * ```swift
 * WidgetBridge.shared.handler = { WidgetSync.refresh() }
 * ```
 *
 * handler 미세팅 상태에서 호출되면 조용히 무시 (부팅 초기 스케줄 저장 등 edge case 방어).
 */
object WidgetBridge {
    var handler: (() -> Unit)? = null
}
