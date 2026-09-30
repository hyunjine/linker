package com.hyunjine.linker.feature.dday

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hyunjine.linker.designsystem.common.FrostedTopBar
import com.hyunjine.linker.designsystem.common.SkeletonBar
import com.hyunjine.linker.designsystem.common.SkeletonBox
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SkeletonLabel
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray

private val TOP_BAR_HEIGHT = 54.dp

/** 스켈레톤 milestone 줄 폭 (이름 · 날짜). 줄마다 달라야 텍스트처럼 보인다. */
private val MilestoneRowWidths = listOf(96 to 72, 120 to 72, 84 to 72, 108 to 72)

/**
 * 디데이 진입 초기 로딩 화면 (#329 · #398). anchor · 프로필 조회가 끝나기 전에 empty state 가
 * 잠깐 노출돼 "설정 안 된 것 처럼" 보이던 문제를 막으려고 따로 둔 화면.
 *
 * [DdayFilledScreen] 과 같은 배치의 스켈레톤 — 프로필 사진 두 장 · 일수 카운터 · 다음 기념일 카드
 * · 탭 · milestone 줄. 대부분 커플은 anchor 가 설정돼 있어 Filled 로 넘어가므로 그 형태를 따른다.
 * 상단바는 로딩 중에도 뒤로가기가 되도록 실제 [FrostedTopBar] 를 그대로 쓴다. 회색 배경 위 자리
 * (사진 · 카운터) 는 대비를 위해 한 톤 진한 `SkeletonLabel`, 흰 카드 안은 기본 `SkeletonFill`.
 *
 * @param onBack 상단바 뒤로가기.
 * @param modifier 루트 modifier.
 */
@Composable
fun DdayLoadingScreen(onBack: () -> Unit = {}, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceGray),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars)),
        ) {
            Spacer(Modifier.height(TOP_BAR_HEIGHT))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(2) {
                    SkeletonBox(RoundedCornerShape(12.dp), Modifier.weight(1f).aspectRatio(1f), color = SkeletonLabel)
                }
            }
            Spacer(Modifier.height(20.dp))
            CounterSkeleton()
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(vertical = 16.dp),
            ) {
                NextMilestoneSkeleton()
                Spacer(Modifier.height(16.dp))
                SkeletonBox(
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(32.dp),
                )
                Spacer(Modifier.height(8.dp))
                MilestoneRowWidths.forEach { (name, date) -> MilestoneRowSkeleton(name, date) }
            }
        }

        FrostedTopBar(
            title = "디데이",
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** 큰 "N일" 카운터 + `YYYY. MM. DD 부터` 서브라인 자리. */
@Composable
private fun CounterSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SkeletonBox(RoundedCornerShape(12.dp), Modifier.width(132.dp).height(64.dp), color = SkeletonLabel)
        Spacer(Modifier.height(14.dp))
        SkeletonBar(width = 136.dp, height = 14.dp, color = SkeletonLabel)
    }
}

/** 다음 기념일 카드 자리 — 좌: 64dp 뱃지, 우: 3줄. */
@Composable
private fun NextMilestoneSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonBox(RoundedCornerShape(14.dp), Modifier.size(64.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBar(width = 64.dp, height = 11.dp)
            SkeletonBar(width = 120.dp, height = 16.dp)
            SkeletonBar(width = 80.dp, height = 12.dp)
        }
    }
}

/**
 * milestone 한 줄 자리 — 좌: 이름 · 날짜, 우: D-뱃지 pill.
 *
 * @param nameWidth 이름 막대 폭 (dp).
 * @param dateWidth 날짜 막대 폭 (dp).
 */
@Composable
private fun MilestoneRowSkeleton(nameWidth: Int, dateWidth: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBar(width = nameWidth.dp, height = 14.dp)
            SkeletonBar(width = dateWidth.dp, height = 12.dp)
        }
        SkeletonBox(RoundedCornerShape(8.dp), Modifier.width(52.dp).height(24.dp))
    }
}

@Composable
@Preview(widthDp = 402, heightDp = 874, showBackground = true)
private fun DdayLoadingScreenPreview() {
    ProvidePretendard { DdayLoadingScreen() }
}
