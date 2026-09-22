package com.hyunjine.linker.feature.dday

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.common.FrostedTopBar
import com.hyunjine.linker.designsystem.common.SegmentedControl
import com.hyunjine.linker.designsystem.common.YearMonthDayPickerSheet
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_edit_pencil
import org.jetbrains.compose.resources.painterResource
import com.hyunjine.linker.designsystem.theme.AvatarPlaceholderBg
import com.hyunjine.linker.designsystem.theme.AvatarPlaceholderFg
import com.hyunjine.linker.designsystem.theme.CalendarBlue
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import kotlinx.datetime.LocalDate

private val TOP_BAR_HEIGHT = 54.dp

/** 큰 "97일" 카운터 컬러 — Figma 4176:79104 (#3E9CFF 계열, CalendarBlue 살짝 옅게). */
private val CounterBlue = Color(0xFF599CFF)

/** D-badge 배경 — 연한 하늘색. */
private val DBadgeBg = Color(0xFFE6F2FF)

/** D-badge 텍스트 컬러 — 브랜드 블루. */
private val DBadgeText = Color(0xFF008AFF)

/**
 * 디데이 설정된 상태 화면 (#329, Figma 4176:79095).
 *
 * 구성 (세로):
 *  1. 두 프로필 사진 (좌: 나, 우: 파트너 — 사진 없으면 이니셜 이니셜 placeholder)
 *  2. 큰 "N일" 카운터 + `YYYY.MM.DD 부터` 서브 + 편집 연필 아이콘
 *  3. 다음 milestone 하이라이트 카드 (D-N badge + 다음 기념일 문구)
 *  4. Segmented control (다가오는 기념일 · 지나온 기념일)
 *  5. milestone 리스트 (탭 별 필터)
 *
 * 편집 연필 · 하이라이트 카드 · empty 상태의 CTA 모두 결국 동일한 [YearMonthDayPickerSheet]
 * 를 열어 새 anchor 를 확정한다.
 */
@Composable
fun DdayFilledScreen(
    anchor: LocalDate,
    today: LocalDate,
    myProfileImageUrl: String? = null,
    myName: String = "",
    partnerProfileImageUrl: String? = null,
    partnerName: String = "",
    onBack: () -> Unit = {},
    onEditAnchor: (LocalDate) -> Unit = {},
) {
    var pickerVisible by remember { mutableStateOf(false) }
    val milestones = remember(anchor, today) { computeMilestones(anchor, today) }
    val upcoming = remember(milestones) { upcomingMilestones(milestones) }
    val past = remember(milestones) { pastMilestones(milestones) }
    val next = upcoming.firstOrNull()

    var tab by remember { mutableStateOf(Tab.Upcoming) }
    val visibleList = if (tab == Tab.Upcoming) upcoming else past
    val daysSince = remember(anchor, today) { daysSinceAnchor(anchor, today) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(TOP_BAR_HEIGHT))
            Spacer(Modifier.height(12.dp))
            ProfilePhotosRow(
                myImageUrl = myProfileImageUrl,
                myName = myName,
                partnerImageUrl = partnerProfileImageUrl,
                partnerName = partnerName,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(20.dp))
            DayCounter(
                days = daysSince,
                anchor = anchor,
                onEditClick = { pickerVisible = true },
            )
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(vertical = 16.dp),
            ) {
                if (next != null) {
                    NextMilestoneCard(milestone = next)
                    Spacer(Modifier.height(16.dp))
                }
                SegmentedControl(
                    options = Tab.entries,
                    selected = tab,
                    onSelect = { tab = it },
                    label = { it.label },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (visibleList.isEmpty()) {
                    Text(
                        text = tab.emptyLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        style = TextStyle(
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = LocalPretendardFontFamily.current,
                        ),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    visibleList.forEach { m -> MilestoneRow(m) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // 반투명 frosted 백 (#329, iOS Wi-Fi 설정 톤 근사). 스크롤 시 뒤 컨텐츠가 top bar
        // 뒤로 지나가도 시각적으로 자연스럽게 분리됨.
        FrostedTopBar(
            title = "디데이",
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    YearMonthDayPickerSheet(
        visible = pickerVisible,
        date = anchor,
        minDate = LocalDate(today.year - 100, 1, 1),
        maxDate = today,
        onConfirm = {
            pickerVisible = false
            onEditAnchor(it)
        },
        onCancel = { pickerVisible = false },
    )
}

/** 프로필 사진 두 장 가로 배치. 각 정사각 · corner 12dp · 12dp gap. */
@Composable
private fun ProfilePhotosRow(
    myImageUrl: String?,
    myName: String,
    partnerImageUrl: String?,
    partnerName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfilePhotoTile(
            imageUrl = myImageUrl,
            fallbackChar = myName.take(1),
            modifier = Modifier.weight(1f).aspectRatio(1f),
        )
        ProfilePhotoTile(
            imageUrl = partnerImageUrl,
            fallbackChar = partnerName.take(1),
            modifier = Modifier.weight(1f).aspectRatio(1f),
        )
    }
}

@Composable
private fun ProfilePhotoTile(
    imageUrl: String?,
    fallbackChar: String,
    modifier: Modifier = Modifier,
) {
    val pretendard = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AvatarPlaceholderBg),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (fallbackChar.isNotBlank()) {
            Text(
                text = fallbackChar,
                style = TextStyle(
                    color = AvatarPlaceholderFg,
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                ),
            )
        }
    }
}

/**
 * 큰 "N일" 카운터 + `YYYY. MM. DD 부터` 서브 + 편집 pencil.
 *
 * Figma 4176:79104 스펙: "97" 은 72sp Bold, 뒤 "일" 은 훨씬 작은 32sp 으로 분리. Row 안에서
 * `alignByBaseline()` 로 두 텍스트 baseline 을 맞춘다.
 */
@Composable
private fun DayCounter(
    days: Int,
    anchor: LocalDate,
    onEditClick: () -> Unit,
) {
    val pretendard = LocalPretendardFontFamily.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$days",
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(
                    color = CounterBlue,
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 72.sp,
                ),
            )
            Text(
                text = "일",
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(
                    color = CounterBlue,
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        // 서브라인 + 연필 아이콘 = 편집 tap 영역. 서브라인 전체를 탭해도 sheet 오픈되도록.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onEditClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${anchor.year}. ${anchor.monthOfYearPadded()}. ${anchor.dayPadded()} 부터",
                style = TextStyle(
                    color = TextSecondary,
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
            )
            Image(
                painter = painterResource(Res.drawable.ic_edit_pencil),
                contentDescription = "디데이 편집",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 다음 도래 milestone 카드 — Figma 4176:79111. 좌: D-badge 64x64, 우: 문구 3줄. */
@Composable
private fun NextMilestoneCard(milestone: DdayMilestone) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DBadgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = milestone.badgeLabel,
                style = TextStyle(
                    color = DBadgeText,
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
            )
        }
        Column {
            Text(
                text = "🎉 다음 기념일",
                style = TextStyle(
                    color = TextSecondary,
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = milestone.label,
                style = TextStyle(
                    color = TextPrimary,
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${milestone.date.year}. ${milestone.date.monthOfYearPadded()}. ${milestone.date.dayPadded()}",
                style = TextStyle(
                    color = TextSecondary,
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

/** 리스트 row — 좌: 이름 + 날짜, 우: D-badge pill. */
@Composable
private fun MilestoneRow(milestone: DdayMilestone) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = milestone.label,
                style = TextStyle(
                    color = TextPrimary,
                    fontFamily = pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${milestone.date.year}.${milestone.date.monthOfYearPadded()}.${milestone.date.dayPadded()}",
                style = TextStyle(
                    color = TextSecondary,
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
            )
        }
        DBadgePill(text = milestone.badgeLabel)
    }
}

@Composable
private fun DBadgePill(text: String) {
    val pretendard = LocalPretendardFontFamily.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DBadgeBg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = DBadgeText,
                fontFamily = pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            ),
        )
    }
}

private enum class Tab(val label: String, val emptyLabel: String) {
    Upcoming("다가오는 기념일", "아직 다가오는 기념일이 없어요."),
    Past("지나온 기념일", "아직 지나온 기념일이 없어요."),
}

private fun LocalDate.monthOfYearPadded(): String =
    (this.month.ordinal + 1).toString().padStart(2, '0')

private fun LocalDate.dayPadded(): String =
    this.day.toString().padStart(2, '0')

@Composable
@Preview(widthDp = 402, heightDp = 874, showBackground = true)
private fun DdayFilledScreenPreview() {
    ProvidePretendard {
        DdayFilledScreen(
            anchor = LocalDate(2025, 3, 22),
            today = LocalDate(2025, 6, 27),
            myName = "현진",
            partnerName = "민교",
        )
    }
}
