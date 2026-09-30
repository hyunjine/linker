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
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** 스켈레톤 최소 노출 시간 (#406). 조회가 빨라도 스켈레톤이 깜빡이듯 사라지지 않도록. */
val SkeletonMinDuration: Duration = 800.milliseconds

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

/**
 * [block] 을 실행하되, 걸린 시간이 [minDuration] 보다 짧으면 남은 시간만큼 기다린 뒤 결과를 돌려준다 (#406).
 * 스켈레톤을 띄운 로딩에서 결과 반영 직전에 감싸 최소 노출 시간을 보장한다. 예외는 그대로 전파.
 *
 * @param minDuration 최소 소요 시간. 기본 [SkeletonMinDuration].
 * @param block 실제 조회.
 */
suspend fun <T> withSkeletonMinDuration(
    minDuration: Duration = SkeletonMinDuration,
    block: suspend () -> T,
): T {
    val start = TimeSource.Monotonic.markNow()
    val result = block()
    val remaining = minDuration - start.elapsedNow()
    if (remaining.isPositive()) delay(remaining)
    return result
}
