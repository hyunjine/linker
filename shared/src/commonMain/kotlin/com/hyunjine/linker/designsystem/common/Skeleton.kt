package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.hyunjine.linker.designsystem.theme.SkeletonFill

/**
 * 로딩 스켈레톤의 텍스트 한 줄 자리. 양 끝이 둥근 막대.
 *
 * @param width 막대 폭.
 * @param height 막대 높이. 모서리 반경은 높이의 절반.
 * @param modifier 배치용 modifier.
 * @param color 채움 색. 기본 [SkeletonFill], 라벨 톤은 `SkeletonLabel`.
 */
@Composable
fun SkeletonBar(width: Dp, height: Dp, modifier: Modifier = Modifier, color: Color = SkeletonFill) {
    Box(
        modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color),
    )
}

/**
 * 로딩 스켈레톤의 이미지 · 뱃지 · 컨트롤 자리. 크기는 [modifier] 로 지정한다.
 *
 * @param shape 모양.
 * @param modifier 크기 · 배치용 modifier.
 * @param color 채움 색. 기본 [SkeletonFill].
 */
@Composable
fun SkeletonBox(shape: Shape, modifier: Modifier = Modifier, color: Color = SkeletonFill) {
    Box(
        modifier
            .clip(shape)
            .background(color),
    )
}
