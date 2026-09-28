package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hyunjine.linker.designsystem.theme.CustomSwatchRainbow
import androidx.compose.ui.unit.dp
import com.hyunjine.linker.designsystem.theme.SurfaceCard

/**
 * 팔레트 끝의 9번째 스와치. iOS 캘린더 앱의 "커스텀 색상" 진입 버튼 톤.
 * 항상 무지개 링 + 안쪽 흰 원 으로 렌더 — 커스터마이즈 진입 affordance 이자, 커스텀 hex 가
 * 현재 선택된 상태에선 [ringColor] 로 주변에 선택 링을 그려 프리셋 스와치와 동일한 하이라이트.
 *
 * 프로필 편집 · 커플 관리 (공동 캘린더 색 #335) 에서 공용.
 *
 * @param ringColor 선택 링 컬러. null 이면 링 없이 rest 상태. 커스텀 hex 가 현재 컬러일 때
 *  해당 hex Color 를 넘겨 다른 프리셋 스와치와 동일한 선택 시각을 준다.
 * @param onClick 탭 — 보통 [CustomColorSheet] 를 연다.
 */
@Composable
fun CustomColorSwatch(
    ringColor: Color?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val rainbow = Brush.sweepGradient(CustomSwatchRainbow)
        if (ringColor != null) {
            // 선택 상태 — 프리셋 스와치와 동일한 사이즈/배치. 외곽 32dp 링 + 내부 22dp 무지개.
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(width = 2.dp, color = ringColor, shape = CircleShape),
            )
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(rainbow),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(SurfaceCard),
                )
            }
        } else {
            // rest — 28dp 무지개 + 18dp 흰 원.
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(rainbow),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(SurfaceCard),
                )
            }
        }
    }
}
