package com.hyunjine.linker.platform

import androidx.compose.runtime.Composable

/**
 * iOS `UISelectionFeedbackGenerator.selectionChanged()` 에 해당하는 짧은 선택-변경 햅틱을 발생시키는
 * 트리거를 기억한다.
 *
 * 반환된 람다를 호출할 때마다 시스템이 짧은 tick 진동을 낸다.
 * - iOS: `UISelectionFeedbackGenerator` 인스턴스를 remember 로 유지하고 매 호출마다 `selectionChanged()`.
 * - Android (프리뷰 전용): no-op.
 *
 * @return 호출 시 selection haptic 을 발생시키는 람다.
 */
@Composable
expect fun rememberSelectionHaptic(): () -> Unit
