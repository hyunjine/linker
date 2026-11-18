package com.hyunjine.linker.feature.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AppBottomSheet
import com.hyunjine.linker.designsystem.common.PrimaryButton
import com.hyunjine.linker.designsystem.common.YearMonthDayPickerSheet
import com.hyunjine.linker.designsystem.theme.AvatarPlaceholderBg
import com.hyunjine.linker.designsystem.theme.Chevron
import com.hyunjine.linker.designsystem.theme.DestructiveRed
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import kotlinx.datetime.LocalDate
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_chevron_right
import org.jetbrains.compose.resources.painterResource

/**
 * 사진을 고른 뒤 뜨는 날짜 확인 시트. 미리보기 + 날짜 행 + 저장 버튼.
 *
 * 날짜 선택 UI 는 기념일 추가 시트와 같은 [YearMonthDayPickerSheet] 를 그대로 쓴다.
 *
 * 업로드가 끝날 때까지 시트를 닫지 않는다 — 진행 · 실패를 사용자가 보던 자리에서 그대로 보여주고,
 * 실패하면 같은 버튼으로 바로 다시 시도할 수 있게 하려는 의도.
 *
 * @param preview 고른 사진 미리보기. null 이면 회색 자리만.
 * @param initialDate 기본 날짜. 사진첩에서 올리면 오늘, 날짜 상세에서 올리면 그 날짜.
 * @param busy 업로드 진행 중. true 면 버튼이 "올리는 중…" 으로 바뀌고 비활성.
 * @param failed 업로드 실패. 안내 문구 + 버튼 라벨이 "다시 시도" 로 바뀐다.
 */
@Composable
fun PhotoDateConfirmSheet(
    visible: Boolean,
    preview: ImageBitmap?,
    initialDate: LocalDate,
    busy: Boolean = false,
    failed: Boolean = false,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
    ) {
        val font = LocalPretendardFontFamily.current
        // visible 이 false → true 로 바뀔 때마다 날짜를 initialDate 로 되돌린다
        // (같은 시트를 재사용해도 앞서 고른 날짜가 남지 않게).
        var date by remember(visible, initialDate) { mutableStateOf(initialDate) }
        var showDatePicker by remember(visible) { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "사진 추가",
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                ),
            )

            // 미리보기 — 사진첩 그리드와 같은 1:1 크롭으로 결과를 예상할 수 있게 한다.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AvatarPlaceholderBg),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = "고른 사진",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceGray)
                    .clickable(enabled = !busy) { showDatePicker = true }
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "날짜",
                    style = TextStyle(
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = font,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatPhotoDate(date),
                    style = TextStyle(color = TextSecondary, fontSize = 16.sp, fontFamily = font),
                )
                Spacer(Modifier.width(6.dp))
                Image(
                    painter = painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Chevron),
                    modifier = Modifier.size(18.dp),
                )
            }

            if (failed) {
                Text(
                    text = "사진을 올리지 못했어요. 다시 시도해 주세요.",
                    style = TextStyle(color = DestructiveRed, fontSize = 13.sp, fontFamily = font),
                )
            }

            PrimaryButton(
                text = when {
                    busy -> "올리는 중…"
                    failed -> "다시 시도"
                    else -> "사진첩에 올리기"
                },
                onClick = { if (!busy) onConfirm(date) },
            )
        }

        YearMonthDayPickerSheet(
            visible = showDatePicker,
            date = date,
            minDate = LocalDate(1900, 1, 1),
            maxDate = LocalDate(date.year + 100, 12, 31),
            onConfirm = {
                showDatePicker = false
                date = it
            },
            onCancel = { showDatePicker = false },
        )
    }
}
