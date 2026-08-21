package com.hyunjine.linker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.SurfaceGray
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextSecondary

/**
 * 앱 전역에서 사용하는 바텀시트. material3의 [ModalBottomSheet]을 감싼 얇은 래퍼이며,
 * 스크림/슬라이드 애니메이션/드래그-다운 dismiss/시스템 백 처리는 모두 material 기본
 * 동작을 그대로 위임한다.
 *
 * @param visible 시트를 표시할지 여부. `false`가 되면 컴포지션에서 완전히 빠지고
 * material 쪽 dismiss 애니메이션이 재생된다.
 * @param onDismissRequest 사용자가 드래그 다운, 스크림 탭, 시스템 백 등으로 닫으려 할 때
 * 호출. 상위에서 [visible] 을 `false` 로 바꿔야 실제 닫힌다.
 * @param modifier 시트 컨테이너에 적용할 [Modifier].
 * @param fullyExpanded `true` 이면 partial peek 상태를 건너뛰고 처음부터 전체 높이로 펼침.
 * 텍스트 입력 시트처럼 콘텐츠가 화면 대부분을 차지해야 하는 경우 사용.
 * 실제로 콘텐츠가 세로를 다 채우려면 컨텐츠에도 `Modifier.fillMaxSize()` 등이 필요.
 * @param dragHandle 상단 드래그 핸들 슬롯. 기본은 [AppDragHandle]. `null` 을 넘기면
 * 핸들이 표시되지 않는다 (예: 자체 X/✓ 툴바를 갖는 편집 시트).
 * @param content 시트 안에 그릴 컨텐츠 슬롯. [ColumnScope] 로 제공된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    fullyExpanded: Boolean = false,
    dragHandle: @Composable (() -> Unit)? = { AppDragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = fullyExpanded)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // 시트 컨테이너 자체를 status bar 만큼 아래로 밀어 상단 corner 가 노출되게 한다.
        // contentWindowInsets 는 콘텐츠 padding 만 조정할 뿐 시트 프레임 자체는 top 까지 확장되어
        // corner radius 가 화면 밖으로 나가 페이지처럼 보이는 문제가 있었다.
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars),
        sheetState = sheetState,
        containerColor = SurfaceCard,
        dragHandle = dragHandle,
        contentWindowInsets = { WindowInsets(0) },
        content = content,
    )
}

/**
 * 시트 상단 중앙의 드래그 핸들. Figma 스펙: 36×5 회색 pill (`#D1D1D6`).
 * material3 기본(32×4)보다 살짝 크고 iOS 톤에 가깝다.
 */
@Composable
private fun AppDragHandle() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(Color(0xFFD1D1D6)),
        )
    }
}

// ---------- Previews ----------
// ModalBottomSheet는 내부에서 Dialog 를 사용하므로 프리뷰 렌더가 제한적일 수 있다.
// 그래서 프리뷰용으로는 실제 sheet 컨테이너 대신 "sheet 내부에 들어갈 콘텐츠"만
// 단독으로 그려 상태별 콘텐츠 확인용도로 쓴다.

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    ProvidePretendard {
        Box(
            Modifier
                .fillMaxSize()
                .background(SurfaceGray)
                .padding(16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard),
            ) {
                Column { content() }
            }
        }
    }
}

@Composable
private fun SampleSheetBody(title: String, lines: Int = 1) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = TextStyle(
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        repeat(lines) {
            Text(
                "샘플 내용 줄 ${it + 1}",
                style = TextStyle(color = TextSecondary, fontSize = 15.sp),
            )
        }
        Spacer(Modifier.size(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AppBottomSheetPreview_Default() {
    PreviewFrame {
        // 드래그 핸들 자리 시뮬레이션
        AppDragHandle()
        SampleSheetBody(title = "기본 상태", lines = 2)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AppBottomSheetPreview_TallContent() {
    PreviewFrame {
        AppDragHandle()
        SampleSheetBody(title = "긴 콘텐츠", lines = 8)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AppBottomSheetPreview_HeaderOnly() {
    PreviewFrame {
        AppDragHandle()
        SampleSheetBody(title = "제목만", lines = 0)
    }
}
