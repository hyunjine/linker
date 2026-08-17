package com.hyunjine.linker.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.theme.AvatarPlaceholderBg
import com.hyunjine.linker.ui.theme.AvatarPlaceholderFg
import com.hyunjine.linker.ui.theme.CalendarBlue
import com.hyunjine.linker.ui.theme.CalendarGray
import com.hyunjine.linker.ui.theme.CalendarGreen
import com.hyunjine.linker.ui.theme.CalendarMint
import com.hyunjine.linker.ui.theme.CalendarOrange
import com.hyunjine.linker.ui.theme.CalendarPink
import com.hyunjine.linker.ui.theme.CalendarPurple
import com.hyunjine.linker.ui.theme.CalendarYellow
import com.hyunjine.linker.ui.theme.Chevron
import com.hyunjine.linker.ui.common.AppBottomSheet
import com.hyunjine.linker.ui.common.WheelPicker
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.OnPrimary
import com.hyunjine.linker.ui.theme.PrimaryBlue
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.Separator
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.SurfaceGray
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextSecondary
import com.hyunjine.linker.ui.theme.TextTertiary

data class CalendarColorOption(val id: String, val color: Color)

val DefaultCalendarColors = listOf(
    CalendarColorOption("blue", CalendarBlue),
    CalendarColorOption("mint", CalendarMint),
    CalendarColorOption("green", CalendarGreen),
    CalendarColorOption("yellow", CalendarYellow),
    CalendarColorOption("orange", CalendarOrange),
    CalendarColorOption("pink", CalendarPink),
    CalendarColorOption("purple", CalendarPurple),
    CalendarColorOption("gray", CalendarGray),
)

// Figma 프레임 402x874 기준.
// 세로 영역 비율 (safe area 안):
//   상태바뒤 → 앱바(54) → 사진섹션(172) → 카드/라벨/컬러(약 190)
//   → 큰 여백(약 264) → CTA(52) → 하단(58, 홈 인디케이터 포함)
// 큰 여백만 weight로 늘려 잡고, 각 블록 사이의 짧은 간격도 정수 비율로 표기.
@Composable
fun ProfileSetupScreen(
    nickname: String = "현진",
    birthDate: String = "1998. 05. 24.",
    selectedColorId: String = "blue",
    calendarColors: List<CalendarColorOption> = DefaultCalendarColors,
    onBack: () -> Unit = {},
    onEditPhoto: () -> Unit = {},
    onEditNickname: () -> Unit = {},
    onEditBirthDate: () -> Unit = {},
    onSelectColor: (String) -> Unit = {},
    onNext: () -> Unit = {},
) {
    // 생년월일 시트 표시 여부. 프로세스 재구성/구성 변경 상황에서도 유지.
    var showBirthDateSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(title = "프로필 편집", onBack = onBack)

        ProfilePhotoSection(onEditPhoto = onEditPhoto)

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            InfoCard(
                nickname = nickname,
                birthDate = birthDate,
                onEditNickname = onEditNickname,
                onEditBirthDate = {
                    showBirthDateSheet = true
                    onEditBirthDate()
                },
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionLabel("내 캘린더 색상")

        Spacer(Modifier.height(11.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            ColorPickerCard(
                colors = calendarColors,
                selectedId = selectedColorId,
                onSelect = onSelectColor,
            )
        }

        // 컬러 카드 아래 → CTA 위의 큰 여백. 화면이 커지면 이 부분만 늘어남.
        Spacer(Modifier.weight(1f).fillMaxHeight())

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            PrimaryButton(text = "다음", onClick = onNext)
        }

        Spacer(Modifier.height(24.dp))
    }

    AppBottomSheet(
        visible = showBirthDateSheet,
        onDismissRequest = { showBirthDateSheet = false },
    ) {
        BirthDatePickerSheet(initial = parseBirthDate(birthDate))
    }
}

private data class BirthDate(val year: Int, val month: Int, val day: Int)

// "1998. 05. 24." 포맷 파싱. 실패 시 오늘 근방의 기본값 반환.
private fun parseBirthDate(raw: String): BirthDate {
    val parts = raw.split('.').mapNotNull { it.trim().toIntOrNull() }
    return if (parts.size == 3) BirthDate(parts[0], parts[1], parts[2]) else BirthDate(2000, 1, 1)
}

private const val YEAR_MIN = 1900
private const val YEAR_MAX = 2026

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

@Composable
private fun BirthDatePickerSheet(initial: BirthDate) {
    // 월/일이 바뀌면 day 상한이 달라지므로 상호 조정.
    var yearIndex by remember { mutableStateOf((initial.year - YEAR_MIN).coerceIn(0, YEAR_MAX - YEAR_MIN)) }
    var monthIndex by remember { mutableStateOf((initial.month - 1).coerceIn(0, 11)) }
    var dayIndex by remember { mutableStateOf((initial.day - 1).coerceIn(0, 30)) }

    val years = remember { (YEAR_MIN..YEAR_MAX).map { "${it}년" } }
    val months = remember { (1..12).map { "${it}월" } }
    val currentYear = YEAR_MIN + yearIndex
    val currentMonth = monthIndex + 1
    val maxDay = daysInMonth(currentYear, currentMonth)
    val days = remember(maxDay) { (1..maxDay).map { "${it}일" } }
    if (dayIndex > maxDay - 1) dayIndex = maxDay - 1

    val wheelItemHeight = 40.dp
    val wheelVisibleCount = 5

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(wheelItemHeight * wheelVisibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // 하이라이트 바 하나 — 3개 컬럼을 관통.
        Box(
            Modifier
                .fillMaxWidth()
                .height(wheelItemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceGray),
        )
        Row(Modifier.fillMaxWidth()) {
            WheelPicker(
                items = years,
                selectedIndex = yearIndex,
                onSelectedChange = { yearIndex = it },
                modifier = Modifier.weight(1f),
                visibleItemCount = wheelVisibleCount,
                itemHeight = wheelItemHeight,
            )
            WheelPicker(
                items = months,
                selectedIndex = monthIndex,
                onSelectedChange = { monthIndex = it },
                modifier = Modifier.weight(1f),
                visibleItemCount = wheelVisibleCount,
                itemHeight = wheelItemHeight,
            )
            WheelPicker(
                items = days,
                selectedIndex = dayIndex,
                onSelectedChange = { dayIndex = it },
                modifier = Modifier.weight(1f),
                visibleItemCount = wheelVisibleCount,
                itemHeight = wheelItemHeight,
            )
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Back button (leading)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceCard)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                ),
            )
        }
        Text(
            text = title,
            style = TextStyle(
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
    }
}

@Composable
private fun ProfilePhotoSection(onEditPhoto: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(AvatarPlaceholderBg)
                .clickable(onClick = onEditPhoto),
            contentAlignment = Alignment.Center,
        ) {
            // 사용자 아바타 미설정 상태의 placeholder. 이후 실제 이미지로 교체.
            Text(
                text = "👤", // 👤
                style = TextStyle(
                    color = AvatarPlaceholderFg,
                    fontSize = 56.sp,
                    fontFamily = font,
                ),
            )
        }
        Text(
            text = "사진 변경",
            modifier = Modifier.clickable(onClick = onEditPhoto),
            style = TextStyle(
                color = PrimaryBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
            ),
        )
    }
}

@Composable
private fun InfoCard(
    nickname: String,
    birthDate: String,
    onEditNickname: () -> Unit,
    onEditBirthDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard),
    ) {
        InfoRow(label = "닉네임", value = nickname, onClick = onEditNickname)
        // 좌측 16dp inset — iOS 스타일 세퍼레이터.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Separator),
            )
        }
        InfoRow(label = "생년월일", value = birthDate, onClick = onEditBirthDate)
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = TextPrimary,
                fontSize = 17.sp,
                fontFamily = font,
            ),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = TextStyle(
                color = TextSecondary,
                fontSize = 17.sp,
                fontFamily = font,
            ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "›",
            style = TextStyle(
                color = Chevron,
                fontSize = 22.sp,
                fontFamily = font,
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val font = LocalPretendardFontFamily.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
        Text(
            text = text,
            style = TextStyle(
                color = TextTertiary,
                fontSize = 13.sp,
                fontFamily = font,
            ),
        )
    }
}

@Composable
private fun ColorPickerCard(
    colors: List<CalendarColorOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        // 8개 스와치가 카드 내부 폭에 맞도록 SpaceBetween으로 균등 분배.
        // 스와치 간 실제 간격은 화면 폭에 따라 자동 계산되며 잘림이 발생하지 않음.
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { option ->
            ColorSwatch(
                color = option.color,
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Figma: 선택된 스와치는 색상 링이 감싸는 형태. 링 두께 2dp, 링과 내부 원 사이 2dp 간격 근사.
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(width = 2.dp, color = color, shape = CircleShape),
            )
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        } else {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PrimaryBlue)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = OnPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
    }
}

@Preview
@Composable
private fun ProfileSetupScreenPreview() {
    ProvidePretendard {
        // Preview에서 상태 확인용으로 remember 사용.
        var selected by remember { mutableStateOf("blue") }
        ProfileSetupScreen(
            selectedColorId = selected,
            onSelectColor = { selected = it },
        )
    }
}
