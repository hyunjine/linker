package com.hyunjine.linker.feature.dday

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SurfaceGray

private val TOP_BAR_HEIGHT = 54.dp

/**
 * 디데이 진입 초기 로딩 화면 (#329). anchor · 프로필 조회가 끝나기 전에 empty state 가 잠깐
 * 노출돼 "설정 안 된 것 처럼" 보이던 UX 문제 해결용.
 *
 * 상단바는 미노출 (뒤로가기가 로딩 중에 필요 없고, 상단바 자체도 render tree 가 준비되기 전이면
 * 반투명 배경 계산이 잘못 나올 수 있어 최소 요소만 남김). 중앙에 spinner 한 개.
 */
@Composable
fun DdayLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceGray),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = PrimaryBlue,
            strokeWidth = 3.dp,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .size(28.dp),
        )
    }
}

@Composable
@Preview(widthDp = 402, heightDp = 874, showBackground = true)
private fun DdayLoadingScreenPreview() {
    ProvidePretendard { DdayLoadingScreen() }
}
