package com.hyunjine.linker.feature.dday

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 디데이 라우트 (#329). VM 이 state (Loading · Empty · Filled) 를 결정하고, 상태에 맞춰
 * empty state 또는 설정된 화면을 렌더한다.
 *
 * anchor 저장은 VM optimistic — 사용자가 시트 완료를 탭하면 UI 는 즉시 Filled 로 전환되고,
 * 백그라운드에서 `couples.dday_anchor_date` UPDATE.
 */
@Composable
fun DdayRoute(
    onBack: () -> Unit,
    onConfirmDate: (kotlinx.datetime.LocalDate) -> Unit = {},
) {
    val vm: DdayViewModel = viewModel { DdayViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    when (val s = state) {
        DdayUiState.Loading -> {
            // 초기 한 프레임만 뜨는 상태. Empty 와 같은 뼈대만 노출 (아이콘 · CTA 는 숨김) 해도
            // 되지만 순간이라 일단 empty state 를 그대로 재사용 (사용자 관점 flicker 는 미미).
            DdayEmptyScreen(onBack = onBack, onConfirmDate = { onConfirm(vm, it, onConfirmDate) })
        }
        DdayUiState.Empty -> {
            DdayEmptyScreen(onBack = onBack, onConfirmDate = { onConfirm(vm, it, onConfirmDate) })
        }
        is DdayUiState.Filled -> {
            DdayFilledScreen(
                anchor = s.anchor,
                today = s.today,
                myProfileImageUrl = s.myImageUrl,
                myName = s.myName,
                partnerProfileImageUrl = s.partnerImageUrl,
                partnerName = s.partnerName,
                onBack = onBack,
                onEditAnchor = { onConfirm(vm, it, onConfirmDate) },
            )
        }
    }
}

private fun onConfirm(
    vm: DdayViewModel,
    date: kotlinx.datetime.LocalDate,
    onConfirmDate: (kotlinx.datetime.LocalDate) -> Unit,
) {
    vm.saveAnchor(date)
    onConfirmDate(date)
}
