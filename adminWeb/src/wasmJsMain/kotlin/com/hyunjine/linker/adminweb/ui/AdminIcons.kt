package com.hyunjine.linker.adminweb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `:adminWeb` 용 미니 아이콘 팩.
 *
 * 유니코드 이모지 / 심볼 (`🔍`, `✓` 등) 은 wasmJs Skia 가 시스템 이모지 폰트에 접근하지 못하고
 * Pretendard 에도 해당 글리프가 없어 `□` (tofu) 로 렌더되는 경우가 있다. 아이콘성 표시는 원시
 * [Canvas] 로 직접 그려 폰트에 의존하지 않게 만든다. Material Icons Extended 의존을 새로
 * 끌어오지 않기 위한 선택.
 */

/**
 * 돋보기 (검색) 아이콘 — 원 렌즈 + 대각선 손잡이.
 *
 * @param modifier `Modifier.size(...)` 로 실제 크기 결정.
 * @param color 스트로크 컬러.
 */
@Composable
fun SearchIcon(
    modifier: Modifier,
    color: Color = Colors.TextTertiary,
) {
    Canvas(modifier = modifier) {
        val strokeW = size.minDimension * 0.14f
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round)
        // 렌즈 원 — 좌상단에 배치.
        val diameter = size.minDimension * 0.62f
        val radius = diameter / 2f
        val cx = radius + strokeW / 2f
        val cy = radius + strokeW / 2f
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(cx, cy),
            style = stroke,
        )
        // 손잡이 — 원 우하단 접점에서 45도 방향으로 뻗음.
        val cos45 = 0.7071f
        val handleStart = Offset(cx + radius * cos45, cy + radius * cos45)
        val handleEnd = Offset(size.width - strokeW / 2f, size.height - strokeW / 2f)
        drawLine(
            color = color,
            start = handleStart,
            end = handleEnd,
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 체크 아이콘 (V 자 두 스트로크).
 *
 * @param modifier `Modifier.size(...)` 로 실제 크기 결정.
 * @param color 스트로크 컬러 (예: 파란 채움 위 흰색).
 * @param strokeWidthDp 스트로크 두께. 기본 2.dp.
 */
@Composable
fun CheckIcon(
    modifier: Modifier,
    color: Color = Colors.OnPrimary,
    strokeWidthDp: Dp = 2.dp,
) {
    Canvas(modifier = modifier) {
        val strokeW = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height
        // V 자 세 점 — 중앙 하단이 꼭짓점.
        val start = Offset(w * 0.18f, h * 0.52f)
        val vertex = Offset(w * 0.42f, h * 0.76f)
        val end = Offset(w * 0.84f, h * 0.28f)
        drawLine(
            color = color,
            start = start,
            end = vertex,
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = vertex,
            end = end,
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}
