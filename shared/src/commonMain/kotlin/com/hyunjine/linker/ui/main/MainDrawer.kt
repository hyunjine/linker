package com.hyunjine.linker.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.platform.rememberSelectionHaptic
import com.hyunjine.linker.ui.theme.AvatarPlaceholderBg
import com.hyunjine.linker.ui.theme.AvatarPlaceholderFg
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.PrimaryBlue
import com.hyunjine.linker.ui.theme.Separator
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextSecondary
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_chevron_right
import linker.shared.generated.resources.ic_gear
import org.jetbrains.compose.resources.painterResource

/** 사이드 드로워의 캘린더 표시 옵션 상태. */
data class DrawerDisplayState(
    val showMyCalendar: Boolean = true,
    val showPartnerCalendar: Boolean = true,
    val showHolidays: Boolean = true,
    val showLunar: Boolean = true,
)

/**
 * 메인 화면 사이드 드로워 콘텐츠. Figma 2693:62775 마지막 panel 참고.
 *
 * 구성:
 *  - 프로필 헤더 (아바타 + 이름/핸들 + [ic_gear] 설정 아이콘)
 *  - "기념일 설정" 진입 row
 *  - "일정 표시" 섹션 — 내 캘린더 / 상대방 캘린더 스위치
 *  - "달력 정보 표시" 섹션 — 공휴일 / 음력 스위치
 *
 * 이 컴포저블은 [AppDrawer] 의 `drawerContent` 슬롯에서 호출됨.
 */
@Composable
fun MainDrawerContent(
    profileName: String,
    profileHandle: String,
    displayState: DrawerDisplayState,
    onSettingsClick: () -> Unit = {},
    onAnniversaryClick: () -> Unit = {},
    onToggleMyCalendar: (Boolean) -> Unit = {},
    onTogglePartnerCalendar: (Boolean) -> Unit = {},
    onToggleHolidays: (Boolean) -> Unit = {},
    onToggleLunar: (Boolean) -> Unit = {},
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
            onSettingsClick = onSettingsClick,
        )
        Spacer(Modifier.height(20.dp))
        NavRow(text = "기념일 설정", onClick = onAnniversaryClick)
        Spacer(Modifier.height(12.dp))
        SectionLabel(text = "일정 표시")
        ToggleRow(
            text = "내 캘린더",
            checked = displayState.showMyCalendar,
            onCheckedChange = onToggleMyCalendar,
        )
        ToggleRow(
            text = "상대방 캘린더",
            checked = displayState.showPartnerCalendar,
            onCheckedChange = onTogglePartnerCalendar,
        )
        Spacer(Modifier.height(12.dp))
        SectionLabel(text = "달력 정보 표시")
        ToggleRow(
            text = "공휴일",
            checked = displayState.showHolidays,
            onCheckedChange = onToggleHolidays,
        )
        ToggleRow(
            text = "음력",
            checked = displayState.showLunar,
            onCheckedChange = onToggleLunar,
        )
    }
}

@Composable
private fun ProfileHeader(name: String, handle: String, onSettingsClick: () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 아바타 placeholder (사진 붙일 때 이미지로 교체)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AvatarPlaceholderBg),
            contentAlignment = Alignment.Center,
        ) {
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
        Spacer(Modifier.size(12.dp))
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
        // 설정 아이콘 — 40dp 원형 탭 타겟 안에 22dp gear 벡터. ripple 은 원형.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_gear),
                contentDescription = "설정",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun NavRow(text: String, onClick: () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Image(
            painter = painterResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val pretendard = LocalPretendardFontFamily.current
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = TextStyle(
            fontFamily = pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = TextSecondary,
        ),
    )
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    val fireHaptic = rememberSelectionHaptic()
    // Row 는 tap 안 받음 → ripple 이 Switch 안에서만. Switch 콜백에서 selection 햅틱 발화.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextPrimary,
            ),
        )
        Switch(
            checked = checked,
            onCheckedChange = { new ->
                fireHaptic()
                onCheckedChange(new)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = SurfaceCard,
                checkedTrackColor = PrimaryBlue,
                uncheckedThumbColor = SurfaceCard,
                uncheckedTrackColor = Separator,
                uncheckedBorderColor = Separator,
            ),
        )
    }
}
