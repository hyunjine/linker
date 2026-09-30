package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import com.hyunjine.linker.designsystem.theme.SkeletonFill
import com.hyunjine.linker.designsystem.theme.SkeletonShimmer
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** 스켈레톤 최소 노출 시간 (#406). 조회가 빨라도 스켈레톤이 깜빡이듯 사라지지 않도록. */
val SkeletonMinDuration: Duration = 800.milliseconds

/** shimmer 한 번이 화면을 가로지르는 주기 (#408). */
private const val ShimmerPeriodNanos = 1_300_000_000L

/** shimmer 빛 띠의 폭 (화면 폭 대비). */
private const val ShimmerBandRatio = 0.45f

/** 빛 띠 기울기 — 띠 폭 대비 세로 이동량. 창 좌표 기준이라 블록 크기와 무관하게 같은 각도. */
private const val ShimmerTilt = 0.35f

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
            .background(color)
            .skeletonShimmer(),
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
            .background(color)
            .skeletonShimmer(),
    )
}

/**
 * 스켈레톤 위로 빛 띠가 좌 → 우로 지나가는 shimmer (#408).
 *
 * 띠 위치는 창 (window) 좌표 기준이고 진행도는 프레임 시각에서 계산하므로, 화면의 모든 스켈레톤 블록이
 * 따로 애니메이션을 돌려도 빛이 한 줄로 맞춰 지나간다. 위치 · 진행도는 draw 단계에서만 읽어
 * 매 프레임 recomposition 없이 다시 그리기만 한다.
 */
@Composable
private fun Modifier.skeletonShimmer(): Modifier {
    val phase by rememberShimmerPhase()
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawWithContent {
            drawContent()
            val band = windowWidth * ShimmerBandRatio
            // 띠 중심이 화면 왼쪽 밖 (-band) 에서 오른쪽 밖 (width + band) 까지 이동. 창 좌표로 계산한 뒤
            // 이 블록의 원점만큼 빼 로컬 좌표로 옮긴다 (그래디언트 방향이 모든 블록에서 같아야 띠가 이어짐).
            val center = -band + phase * (windowWidth + band * 2)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, SkeletonShimmer, Color.Transparent),
                    start = Offset(center - band / 2, 0f) - origin,
                    end = Offset(center + band / 2, band * ShimmerTilt) - origin,
                ),
            )
        }
}

/** 프레임 시각으로 계산한 shimmer 진행도 0f..1f. 같은 프레임의 모든 호출자가 같은 값을 받는다. */
@Composable
private fun rememberShimmerPhase(): State<Float> = produceState(0f) {
    while (true) {
        withFrameNanos { now -> value = (now % ShimmerPeriodNanos).toFloat() / ShimmerPeriodNanos }
    }
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
