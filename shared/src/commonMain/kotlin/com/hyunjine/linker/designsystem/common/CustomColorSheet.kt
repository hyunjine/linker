package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.hyunjine.linker.designsystem.theme.CalendarBlue
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray

/**
 * 캘린더 커스텀 컬러 편집 시트. 사용자가 프리셋 팔레트 밖의 컬러를 지정할 수 있게 한다 (#247).
 *
 * 구성:
 *  - HSV 색상휠 (skydoves colorpicker-compose) — 시각적 선택
 *  - Brightness 슬라이더 — 밝기 조절
 *  - hex TextField — 정확한 값 · 브랜드 컬러 직접 입력 · 카피/붙여넣기
 *
 * 상단 툴바 X | 타이틀 | ✓ 는 닉네임 시트와 동일 (일관성).
 *
 * @param visible 시트 표시 여부. `false` 면 컴포지션에서 빠지고 material dismiss 애니메이션.
 * @param initialHex 최초 표시할 hex 문자열. `#` prefix 유무 무관 · 6/8자리 모두 허용.
 * @param onDismissRequest 스크림 · 백 · X 로 닫힐 때. 저장 없이 취소.
 * @param onConfirm 유효한 6자리 hex 를 확정할 때. `"#RRGGBB"` 대문자 형식으로 넘어옴.
 */
@Composable
fun CustomColorSheet(
    visible: Boolean,
    initialHex: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AppBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        // 자체 X/✓ 툴바를 갖는 편집 시트 컨벤션 — 드래그 핸들 숨김.
        dragHandle = null,
    ) {
        CustomColorSheetContent(
            initialHex = initialHex,
            onCancel = onDismissRequest,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun CustomColorSheetContent(
    initialHex: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val controller = rememberColorPickerController()

    // "#" 제외한 6자리 대문자 hex. 사용자가 hex 필드에서 편집하면 여기서 관리.
    // ColorPicker 조작 시엔 onColorChanged 콜백이 이 값을 자동 갱신.
    var hex by remember { mutableStateOf(sanitizeInitialHex(initialHex)) }
    // 지금 이 변화가 hex 필드 입력에서 온 건지, 컬러 피커 제스처에서 온 건지 구분.
    // 필드 → 피커 setWheelColor 는 리엔트런시 방지를 위해 필요.
    var pickerDrivenTick by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    // 시트가 열리면 피커에 초기 컬러 세팅 (initialHex 유효할 때만).
    LaunchedEffect(Unit) {
        val initial = previewColorFor(sanitizeInitialHex(initialHex))
        controller.selectByColor(initial, fromUser = false)
    }

    // hex 필드가 6자리로 완성되면 그 값을 피커에 반영. onColorChanged 재발화로 인한
    // 무한 루프는 아래 콜백에서 fromUser 로 구분해 방어.
    LaunchedEffect(hex) {
        if (hex.length == 6 && pickerDrivenTick == 0) {
            controller.selectByColor(previewColorFor(hex), fromUser = false)
        }
    }

    val previewColor = remember(hex) { previewColorFor(hex) }
    val isValid = hex.length == 6

    fun submit() {
        if (isValid) onConfirm("#$hex")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetToolbar(
            title = "커스텀 색상",
            onCancel = onCancel,
            onConfirm = { submit() },
            confirmEnabled = isValid,
        )

        // HSV 색상휠 — 사용자가 원형 팔레트에서 색조/채도 선택.
        HsvColorPicker(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(horizontal = 8.dp),
            controller = controller,
            onColorChanged = { envelope: ColorEnvelope ->
                // 사용자 제스처로 발생한 변경만 hex 로 전파. selectByColor(fromUser=false)
                // 에서 온 콜백은 무시해 hex ↔ picker 순환 방지.
                if (envelope.fromUser) {
                    pickerDrivenTick++
                    hex = envelope.hexCode.takeLast(6).uppercase() // "AARRGGBB" 형식이라 뒤 6자만.
                    pickerDrivenTick--
                }
            },
        )

        // Brightness 슬라이더 — 색조는 유지하고 명도만 조절.
        BrightnessSlider(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 8.dp),
            controller = controller,
        )

        // 현재 컬러 프리뷰 스와치 + hex 입력. 동일 행에 배치해 값과 색을 한눈에.
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(width = 48.dp, height = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewColor)
                    .align(Alignment.CenterStart),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp)
                    .align(Alignment.Center),
            ) {
                AppInputCard(
                    label = "HEX",
                    value = hex,
                    onValueChange = { raw ->
                        // 유효 hex 문자만 통과 · 대문자 · 최대 6자리.
                        hex = raw.filter { it.isHexChar() }.uppercase().take(6)
                    },
                    placeholder = "RRGGBB",
                    focusRequester = focusRequester,
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    onImeAction = { submit() },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

/** 입력 문자열에서 hex 로 유효한 부분만 추출해 대문자 6자리로 잘라낸다. */
private fun sanitizeInitialHex(input: String): String =
    input.removePrefix("#").filter { it.isHexChar() }.uppercase().take(6)

/**
 * 현재 입력 문자열의 프리뷰 색상.
 *  - 완성된 6자리 → 그대로 파싱
 *  - 미완성 → 부족한 자리를 `0` 으로 채워 대략적 프리뷰
 */
private fun previewColorFor(hex: String): Color {
    val padded = hex.padEnd(6, '0')
    val v = padded.toLongOrNull(16) ?: return CalendarBlue
    val r = ((v shr 16) and 0xFF).toInt()
    val g = ((v shr 8) and 0xFF).toInt()
    val b = (v and 0xFF).toInt()
    return Color(red = r, green = g, blue = b)
}

private fun Char.isHexChar(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

// ---------- Previews ----------
// AppBottomSheet 은 Dialog 기반이라 프리뷰에서 렌더 안 됨. 시트 콘텐츠만 프레임에 꽂는다.

@Composable
private fun SheetPreviewFrame(content: @Composable () -> Unit) {
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

@Preview
@Composable
private fun CustomColorSheetPreview_Empty() {
    SheetPreviewFrame {
        CustomColorSheetContent(
            initialHex = "",
            onCancel = {},
            onConfirm = {},
        )
    }
}

@Preview
@Composable
private fun CustomColorSheetPreview_Prefilled() {
    SheetPreviewFrame {
        CustomColorSheetContent(
            initialHex = "#FF6B35",
            onCancel = {},
            onConfirm = {},
        )
    }
}
