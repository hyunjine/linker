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
            // 로딩이 실제 network round-trip 을 타서 empty state 를 잠깐 보여주면 "설정 안 된 것" 처럼
            // 오해되는 UX 문제가 있어 (#329) 로딩 중엔 별도 스크린으로 명확히 구분.
            DdayLoadingScreen()
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
