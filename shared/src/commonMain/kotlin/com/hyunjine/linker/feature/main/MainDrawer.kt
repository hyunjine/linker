package com.hyunjine.linker.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hyunjine.linker.designsystem.theme.AvatarPlaceholderBg
import com.hyunjine.linker.designsystem.theme.AvatarPlaceholderFg
import com.hyunjine.linker.designsystem.theme.DrawerButtonBg
import com.hyunjine.linker.designsystem.theme.DrawerCheckBlue
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_app_logo
import linker.shared.generated.resources.ic_cal_31
import linker.shared.generated.resources.ic_check
import linker.shared.generated.resources.ic_setting_outline
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** 사이드 드로워의 캘린더 표시 옵션 상태. */
data class DrawerDisplayState(
    val showMyCalendar: Boolean = true,
    val showPartnerCalendar: Boolean = true,
    /** 공동 (Us) 일정 · 할일 표시. 내 · 상대방과 독립적으로 켤/끌 수 있음. */
    val showSharedCalendar: Boolean = true,
    val showHolidays: Boolean = true,
    val showSolarTerms: Boolean = true,
)

/**
 * 메인 화면 사이드 드로워 콘텐츠. Figma 3114:76134 참고.
 *
 * 구성:
 *  - 프로필 헤더 (아바타 + 이름/핸들 + 설정 아이콘) — Row 전체 탭 → [onSettingsClick]
 *  - "기념일 설정" 진입 row
 *  - "일정 표시" 섹션 — 내 캘린더 / 상대방 캘린더
 *  - "달력 정보 표시" 섹션 — 공휴일 / 절기
 *
 * 이 컴포저블은 [AppDrawer] 의 `drawerContent` 슬롯에서 호출됨.
 */
@Composable
fun MainDrawerContent(
    profileName: String,
    profileHandle: String,
    displayState: DrawerDisplayState,
    profileImageUrl: String? = null,
    onSettingsClick: () -> Unit = {},
    onAnniversaryClick: () -> Unit = {},
    onCoupleLinkClick: () -> Unit = {},
    onToggleMyCalendar: (Boolean) -> Unit = {},
    onTogglePartnerCalendar: (Boolean) -> Unit = {},
    onToggleSharedCalendar: (Boolean) -> Unit = {},
    onToggleHolidays: (Boolean) -> Unit = {},
    onToggleSolarTerms: (Boolean) -> Unit = {},
    onLogout: () -> Unit = {},
    /** 파트너 조인 여부. false 면 "상대방 캘린더" · "공동 캘린더" 토글 자체를 감춘다. */
    hasPartner: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard),
    ) {
        Spacer(Modifier.height(16.dp))
        ProfileHeader(
            name = profileName,
            handle = profileHandle,
            imageUrl = profileImageUrl,
            onClick = onSettingsClick,
        )
        Spacer(Modifier.height(12.dp))
        AllScheduleButtonWithLogo(
            text = "상대방 연결",
            onClick = onCoupleLinkClick,
        )
        Spacer(Modifier.height(8.dp))
        AllScheduleButton(
            text = "기념일 설정",
            iconRes = Res.drawable.ic_cal_31,
            onClick = onAnniversaryClick,
        )
        Spacer(Modifier.height(12.dp))
        SectionLabel(text = "일정 표시")
        ToggleRow(
            text = "내 캘린더",
            checked = displayState.showMyCalendar,
            onCheckedChange = onToggleMyCalendar,
        )
        if (hasPartner) {
            // 상대방 · 공동 개념은 파트너가 있을 때만 의미. Solo 상태에선 감춰서 사용자 혼란 방지.
            ToggleRow(
                text = "상대방 캘린더",
                checked = displayState.showPartnerCalendar,
                onCheckedChange = onTogglePartnerCalendar,
            )
            ToggleRow(
                text = "공동 캘린더",
                checked = displayState.showSharedCalendar,
                onCheckedChange = onToggleSharedCalendar,
            )
        }
        SectionLabel(text = "달력 정보 표시")
        ToggleRow(
            text = "공휴일",
            checked = displayState.showHolidays,
            onCheckedChange = onToggleHolidays,
        )
        ToggleRow(
            text = "절기",
            checked = displayState.showSolarTerms,
            onCheckedChange = onToggleSolarTerms,
        )
        Spacer(Modifier.height(16.dp))
        LogoutRow(onClick = onLogout)
        Spacer(Modifier.height(16.dp))
    }
}

/** 드로워 하단 로그아웃 버튼. 강조 색 없이 텍스트만 (좌측 정렬). */
@Composable
private fun LogoutRow(onClick: () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "로그아웃",
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextSecondary,
            ),
        )
    }
}

/**
 * 프로필 헤더. Figma 3114:76145. Row 전체가 탭 타겟 — 톱니 아이콘만이 아니라
 * 사진/이름/핸들 어디를 눌러도 프로필 수정 화면으로 진입.
 */
@Composable
private fun ProfileHeader(name: String, handle: String, imageUrl: String?, onClick: () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AvatarPlaceholderBg),
            contentAlignment = Alignment.Center,
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "프로필 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                )
            } else {
                Text(
                    text = name.take(1),
                    style = TextStyle(
                        fontFamily = pretendard,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = AvatarPlaceholderFg,
                    ),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                ),
            )
            if (handle.isNotBlank()) {
                Text(
                    text = handle,
                    style = TextStyle(
                        fontFamily = pretendard,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = TextSecondary,
                    ),
                )
            }
        }
        // 설정 아이콘 — outline 스타일 24dp. Row 전체가 탭 타겟이므로 이 아이콘 자체는 시각 요소.
        Image(
            painter = painterResource(Res.drawable.ic_setting_outline),
            contentDescription = null,
            colorFilter = ColorFilter.tint(TextPrimary),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * "상대방 연결" 전용 — 앱 아이콘 (링키드 링) 을 22dp 로 렌더. 그 외 시각·간격은 [AllScheduleButton] 과 동일.
 */
@Composable
private fun AllScheduleButtonWithLogo(
    text: String,
    onClick: () -> Unit,
) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DrawerButtonBg)
            .noRippleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)),
        )
        Text(
            text = text,
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary,
            ),
        )
    }
}

/**
 * Figma "AllScheduleBtn" 재현. 회색 라운드 컨테이너 + 좌측 22dp 아이콘 + Bold 15sp 텍스트.
 * 우측 chevron 없음 (Figma 사양). 리플은 라운드 사각형으로 잘림.
 */
@Composable
private fun AllScheduleButton(
    text: String,
    iconRes: DrawableResource,
    onClick: () -> Unit,
) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DrawerButtonBg)
            .noRippleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary,
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val pretendard = LocalPretendardFontFamily.current
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        style = TextStyle(
            fontFamily = pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = TextSecondary,
        ),
    )
}

/**
 * Figma 드로워 표시 옵션 row. 좌측 22dp 사각 체크박스 + 15sp 라벨. Row 전체가 탭 타겟 (토글).
 * 체크 상태 = 파란 fill + 흰 체크마크, 언체크 = 파란 테두리만.
 * (Figma 에는 우측 chevron 도 있지만 세부 필터 화면이 아직 없어 이번 스코프에서 제외.)
 */
@Composable
private fun ToggleRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckboxSquare(checked = checked)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextPrimary,
            ),
        )
    }
}

/** Figma 체크박스: 22dp · rounded 5 · 체크 시 fill, 언체크 시 2dp 파란 테두리. */
@Composable
private fun CheckboxSquare(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .then(
                if (checked) Modifier.background(DrawerCheckBlue)
                else Modifier.border(2.dp, DrawerCheckBlue, RoundedCornerShape(5.dp)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Image(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 리플 없는 clickable. 드로워/시트처럼 자체 시각 피드백 (체크박스 색 변화 등) 이 있는 곳용. */
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
