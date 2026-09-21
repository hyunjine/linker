package com.hyunjine.linker.feature.dday

import androidx.compose.runtime.Composable

/**
 * 디데이 라우트 (#329).
 *
 * 현재 스코프: 미설정 상태 화면만. 사용자가 시트에서 날짜를 확정하면 [onConfirmDate] 로 상위에
 * 전달 — 저장 · 설정된 상태 UI 로 전환은 후속 커밋. 우선은 사용자가 흐름을 눈으로 확인할 수
 * 있게 empty state + 시트 트리거까지만 붙임.
 */
@Composable
fun DdayRoute(
    onBack: () -> Unit,
    onConfirmDate: (kotlinx.datetime.LocalDate) -> Unit = {},
) {
    DdayEmptyScreen(
        onBack = onBack,
        onConfirmDate = onConfirmDate,
    )
}
