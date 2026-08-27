package com.hyunjine.linker.ui.profile

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hyunjine.linker.platform.rememberImagePicker
import com.hyunjine.linker.ui.common.AppBottomSheet
import com.hyunjine.linker.ui.common.AppTopBar
import com.hyunjine.linker.ui.common.PrimaryButton
import com.hyunjine.linker.ui.common.SectionLabel
import com.hyunjine.linker.ui.common.YearMonthDayPickerSheet
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
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.OnPrimary
import com.hyunjine.linker.ui.theme.PrimaryBlue
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.Separator
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.SurfaceGray
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextSecondary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    saving: Boolean = false,
    /** 카카오 provider `avatar_url` 등 외부에서 넘어온 기본 아바타 URL. 사용자가 직접 사진을 고르면 그게 우선. */
    defaultAvatarUrl: String? = null,
    /** CTA 라벨. 온보딩은 "다음", 편집은 "저장" 등 상위에서 결정. */
    submitText: String = "다음",
    onBack: () -> Unit = {},
    onEditPhoto: () -> Unit = {},
    onNicknameChange: (String) -> Unit = {},
    onBirthDateChange: (String) -> Unit = {},
    onSelectColor: (String) -> Unit = {},
    onNext: (nickname: String, birthDate: LocalDate?, colorId: String) -> Unit = { _, _, _ -> },
) {
    // 시트 표시 여부. 프로세스 재구성/구성 변경 상황에서도 유지.
    var showBirthDateSheet by rememberSaveable { mutableStateOf(false) }
    var showNicknameSheet by rememberSaveable { mutableStateOf(false) }
    // 화면이 직접 소유하는 편집 상태 (uncontrolled). 상위는 콜백으로만 최종 값을 수신.
    var currentNickname by rememberSaveable { mutableStateOf(nickname) }
    var currentBirthDate by rememberSaveable { mutableStateOf(birthDate) }
    var currentColorId by rememberSaveable { mutableStateOf(selectedColorId) }
    // 사용자가 사진 라이브러리에서 고른 아바타. 메모리 전용 (파일 저장은 이후).
    // rememberSaveable 은 ImageBitmap 을 저장할 수 없어 remember 로만 유지.
    var avatarImage by remember { mutableStateOf<ImageBitmap?>(null) }
    val launchPhotoPicker = rememberImagePicker { picked ->
        if (picked != null) avatarImage = picked
    }
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
            // 상단 앱바가 오버레이로 뜨므로 콘텐츠는 그 높이만큼 시작 지점을 밀어준다.
            Spacer(Modifier.height(54.dp))

            ProfilePhotoSection(
                image = avatarImage,
                defaultAvatarUrl = defaultAvatarUrl,
                onEditPhoto = {
                    launchPhotoPicker()
                    onEditPhoto()
                },
            )

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                InfoCard(
                    nickname = currentNickname,
                    birthDate = currentBirthDate,
                    onEditNickname = { showNicknameSheet = true },
                    onEditBirthDate = { showBirthDateSheet = true },
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionLabel("내 캘린더 색상")

            Spacer(Modifier.height(11.dp))

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ColorPickerCard(
                    colors = calendarColors,
                    selectedId = currentColorId,
                    onSelect = {
                        currentColorId = it
                        onSelectColor(it)
                    },
                )
            }

            // 컬러 카드 아래 → CTA 위의 큰 여백. 화면이 커지면 이 부분만 늘어남.
            Spacer(Modifier.weight(1f).fillMaxHeight())

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                PrimaryButton(
                    text = if (saving) "저장 중…" else submitText,
                    onClick = {
                        if (saving) return@PrimaryButton
                        val parsed = parseBirthDate(currentBirthDate)
                        val date = runCatching { LocalDate(parsed.year, parsed.month, parsed.day) }.getOrNull()
                        onNext(currentNickname.trim(), date, currentColorId)
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // 상단 오버레이 앱바.
        AppTopBar(
            title = "프로필 편집",
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }

    val parsed = parseBirthDate(currentBirthDate)
    // 생년월일 범위: 1900-01-01 ~ 오늘. 미래 날짜는 아예 목록에서 제외.
    YearMonthDayPickerSheet(
        visible = showBirthDateSheet,
        date = LocalDate(parsed.year, parsed.month, parsed.day),
        minDate = LocalDate(1900, 1, 1),
        maxDate = today(),
        onConfirm = { picked ->
            showBirthDateSheet = false
            val next = formatBirthDate(
                BirthDate(picked.year, picked.month.ordinal + 1, picked.day),
            )
            if (next != currentBirthDate) {
                currentBirthDate = next
                onBirthDateChange(next)
            }
        },
        onCancel = { showBirthDateSheet = false },
    )

    AppBottomSheet(
        visible = showNicknameSheet,
        onDismissRequest = { showNicknameSheet = false },
        fullyExpanded = true, // 텍스트 입력 시트는 화면을 거의 채우도록 초기부터 fully expanded
        dragHandle = null,    // 자체 X/✓ 툴바를 그리므로 드래그 핸들 숨김
    ) {
        NicknameEditSheet(
            initial = currentNickname,
            onCancel = { showNicknameSheet = false },
            onConfirm = { newName ->
                showNicknameSheet = false
                currentNickname = newName
                onNicknameChange(newName)
            },
        )
    }
}

private fun formatBirthDate(d: BirthDate): String =
    "${d.year}. ${d.month.toString().padStart(2, '0')}. ${d.day.toString().padStart(2, '0')}."

@OptIn(ExperimentalTime::class)
private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private data class BirthDate(val year: Int, val month: Int, val day: Int)

// "1998. 05. 24." 포맷 파싱. 실패 시 오늘 근방의 기본값 반환.
private fun parseBirthDate(raw: String): BirthDate {
    val parts = raw.split('.').mapNotNull { it.trim().toIntOrNull() }
    return if (parts.size == 3) BirthDate(parts[0], parts[1], parts[2]) else BirthDate(2000, 1, 1)
}

@Composable
private fun ProfilePhotoSection(
    image: ImageBitmap?,
    defaultAvatarUrl: String?,
    onEditPhoto: () -> Unit,
) {
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
            // 표시 우선순위: 사용자가 방금 고른 로컬 이미지 > 카카오 avatar_url > 👤 placeholder.
            when {
                image != null -> Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(100.dp).clip(CircleShape),
                )
                defaultAvatarUrl != null -> AsyncImage(
                    model = defaultAvatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(100.dp).clip(CircleShape),
                )
                else -> Text(
                    text = "👤",
                    style = TextStyle(
                        color = AvatarPlaceholderFg,
                        fontSize = 56.sp,
                        fontFamily = font,
                    ),
                )
            }
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

/**
 * 닉네임 편집 시트 콘텐츠. AppBottomSheet 안에 배치되며, 자체 상단 툴바
 * (X 닫기 / 닉네임 타이틀 / ✓ 확인)를 그리므로 시트의 드래그 핸들은 숨긴다.
 *
 * @param initial 시트가 열릴 때 표시할 초기 닉네임. 최초 focus 시 커서가 문자열 끝에 위치.
 * @param onCancel X 버튼 또는 시트 dismiss 시 호출. 저장 없이 닫는 신호.
 * @param onConfirm ✓ 버튼 또는 키보드 return 시 호출. 최종 확정 닉네임을 전달.
 */
@Composable
private fun NicknameEditSheet(
    initial: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val font = LocalPretendardFontFamily.current
    var value by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    // 시트가 열리면 즉시 필드에 포커스 → iOS/Android 모두 시스템 키보드 자동 표시.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()          // fullyExpanded 시트가 실제로 세로를 다 차지하도록 확장
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 상단 툴바: X | 닉네임 | ✓
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            CircleIconButton(
                symbol = "✕",
                background = SurfaceCard,
                iconColor = TextPrimary,
                iconWeight = FontWeight.Medium,
                iconSize = 18.sp,
                onClick = onCancel,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                text = "닉네임",
                modifier = Modifier.align(Alignment.Center),
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                ),
            )
            CircleIconButton(
                symbol = "✓",
                background = PrimaryBlue,
                iconColor = OnPrimary,
                iconWeight = FontWeight.Bold,
                iconSize = 22.sp,
                onClick = { onConfirm(value.trim()) },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        com.hyunjine.linker.ui.common.AppInputCard(
            label = "닉네임",
            value = value,
            onValueChange = { value = it },
            focusRequester = focusRequester,
            onImeAction = { onConfirm(value.trim()) },
        )
    }
}

/**
 * 원형 아이콘 버튼 — X / ✓ 같은 간단한 심볼용.
 * 별도 Icon 리소스 없이 유니코드 문자만으로 그린다.
 *
 * @param symbol 표시할 문자(예: "✕", "✓").
 * @param background 배경 원 색상.
 * @param iconColor 심볼 색상.
 * @param iconWeight 심볼 폰트 굵기.
 * @param iconSize 심볼 폰트 크기.
 * @param onClick 탭 콜백.
 * @param modifier 외부 [Modifier].
 * @param diameter 원 지름 (기본 44dp — 44pt 최소 터치 영역).
 */
@Composable
private fun CircleIconButton(
    symbol: String,
    background: Color,
    iconColor: Color,
    iconWeight: FontWeight,
    iconSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = TextStyle(
                color = iconColor,
                fontSize = iconSize,
                fontWeight = iconWeight,
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
        var name by remember { mutableStateOf("현진") }
        ProfileSetupScreen(
            nickname = name,
            selectedColorId = selected,
            onSelectColor = { selected = it },
            onNicknameChange = { name = it },
        )
    }
}
