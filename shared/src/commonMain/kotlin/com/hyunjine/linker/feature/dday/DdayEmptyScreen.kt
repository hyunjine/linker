package com.hyunjine.linker.feature.dday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.FrostedTopBar
import com.hyunjine.linker.designsystem.common.YearMonthDayPickerSheet
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val TOP_BAR_HEIGHT = 54.dp

/** Empty state 아이콘 · 텍스트에 쓰이는 muted 회색 톤. Figma spec (#329). */
private val EmptyMuted = Color(0xFFBDBDC3)

/** Empty state 아이콘 배경 (밝은 회색 정사각). Figma spec. */
private val EmptyIconBg = Color(0xFFF2F2F7)

/**
 * 디데이(D-day) 미설정 상태 화면 (#329).
 *
 * 구성 (세로 중앙 정렬):
 *  - 80dp 회색 캘린더 아이콘 + 안쪽 "D" 오버레이
 *  - 제목 "아직 디데이가 없어요"
 *  - 서브 "특별한 날을 설정하고\n함께한 시간을 확인해보세요"
 *  - 파란 CTA "디데이 설정하기" → [YearMonthDayPickerSheet] 오픈
 *
 * CTA 로 열린 시트에서 [onConfirmDate] 로 확정 날짜가 상위로 전달되면, 상위 라우트가 저장 후
 * "설정된 상태" 화면으로 전환한다 (설정된 상태 UI 는 후속 스코프).
 *
 * @param onBack 상단 뒤로가기.
 * @param onConfirmDate 시트 완료 탭 시 콜백. 저장은 상위 (라우트/VM) 담당.
 */
@Composable
fun DdayEmptyScreen(
    onBack: () -> Unit = {},
    onConfirmDate: (LocalDate) -> Unit = {},
) {
    var pickerVisible by remember { mutableStateOf(false) }
    // 시트 첫 노출 시 기본 년/월/일 — 오늘. 매 노출마다 초기화 (상위에서 관리하지 않음).
    val defaultDate = remember { defaultToday() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Spacer(Modifier.height(TOP_BAR_HEIGHT))
            EmptyContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 40.dp), // 시각적 balance — 홈 인디케이터 위 여백
                onSetupClick = { pickerVisible = true },
            )
        }

        // 반투명 frosted 백 (#329, DdayFilledScreen 과 동일 톤).
        FrostedTopBar(
            title = "디데이",
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    // 날짜 선택 시트 — 공통 컴포넌트 재사용. 범위는 과거 100년 ~ 오늘 (미래 디데이 = 카운트다운은 후속).
    YearMonthDayPickerSheet(
        visible = pickerVisible,
        date = defaultDate,
        minDate = LocalDate(defaultDate.year - 100, 1, 1),
        maxDate = defaultDate,
        onConfirm = {
            pickerVisible = false
            onConfirmDate(it)
        },
        onCancel = { pickerVisible = false },
    )
}

@Composable
private fun EmptyContent(modifier: Modifier, onSetupClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DdayEmptyIcon()
        Spacer(Modifier.height(20.dp))
        Text(
            text = "아직 디데이가 없어요",
            style = TextStyle(
                color = TextPrimary,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "특별한 날을 설정하고\n함께한 시간을 확인해보세요",
            style = TextStyle(
                color = TextSecondary,
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryCta(
            text = "디데이 설정하기",
            onClick = onSetupClick,
        )
    }
}

/**
 * 80dp 회색 정사각 배경 위에 캘린더 프레임 + 상단 divider + 중앙 "D" 로 D-day 뉘앙스를 준
 * empty-state 아이콘. Figma 4180:63389 좌표 그대로 매핑 (본체 · 두 hanger · divider · "D").
 * 이 화면 전용이라 별도 vector drawable 로 뽑지 않고 절대 offset 으로 조합.
 */
@Composable
private fun DdayEmptyIcon() {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(EmptyIconBg),
    ) {
        // 캘린더 본체 (라운드 사각 스트로크) — 48x44 at (16, 22).
        Box(
            modifier = Modifier
                .offset(x = 16.dp, y = 22.dp)
                .size(width = 48.dp, height = 44.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, EmptyMuted, RoundedCornerShape(6.dp)),
        )
        // 좌측 hanger — 3x10 at (26, 16).
        Box(
            modifier = Modifier
                .offset(x = 26.dp, y = 16.dp)
                .size(width = 3.dp, height = 10.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(EmptyMuted),
        )
        // 우측 hanger — 3x10 at (51, 16).
        Box(
            modifier = Modifier
                .offset(x = 51.dp, y = 16.dp)
                .size(width = 3.dp, height = 10.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(EmptyMuted),
        )
        // 상단 divider — 48x2 at (16, 33).
        Box(
            modifier = Modifier
                .offset(x = 16.dp, y = 33.dp)
                .size(width = 48.dp, height = 2.dp)
                .background(EmptyMuted),
        )
        // "D" 텍스트 — 캘린더 본체 폭 (48dp) 전체에 걸친 Box 안에서 가운데 정렬.
        // Text 자체에 offset 을 주면 폰트 글리프 폭에 따라 시각 중심이 어긋나므로,
        // 캘린더 본체와 정확히 겹치는 컨테이너를 만들어 그 안에서 Center align.
        Box(
            modifier = Modifier
                .offset(x = 16.dp, y = 22.dp)
                .size(width = 48.dp, height = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "D",
                // 상단 divider (본체 상단 11dp 지점) 아래 중앙으로 살짝 내려서 시각적 균형.
                modifier = Modifier.offset(y = 6.dp),
                style = TextStyle(
                    color = EmptyMuted,
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
            )
        }
    }
}

@Composable
private fun PrimaryCta(text: String, onClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PrimaryBlue)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = Color.White,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
        )
    }
}

/** Screen 진입 시 picker 기본값 = 오늘. */
@OptIn(ExperimentalTime::class)
private fun defaultToday(): LocalDate {
    // 프리뷰 · 유닛테스트에서도 안전한 fallback (Clock 실패 시 임의값).
    return runCatching {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }.getOrDefault(LocalDate(2025, 1, 1))
}

@Composable
@Preview(widthDp = 402, heightDp = 874, showBackground = true)
private fun DdayEmptyScreenPreview() {
    ProvidePretendard {
        DdayEmptyScreen()
    }
}
