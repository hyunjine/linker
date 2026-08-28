package com.hyunjine.linker.designsystem.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hyunjine.linker.platform.rememberSelectionHaptic
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.Separator
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray

// iOS 네이티브 UISwitch 규격을 따라간다. TrackWidth 는 프로젝트 톤에 맞춰 살짝 늘렸다.
// - Track 54×31, 완전한 pill
// - Thumb 27dp 원형, 상하좌우 2dp inset. 체크 여부와 무관하게 항상 동일 크기.
// - 슬라이드 이동 거리: 54 - 27 - 2*2 = 23dp
private val TrackWidth = 54.dp
private val TrackHeight = 31.dp
private val ThumbSize = 27.dp
private val ThumbInset = 2.dp
private val ThumbTravel = TrackWidth - ThumbSize - ThumbInset * 2

/**
 * 앱 공통 스위치. iOS UISwitch 시각 규격을 그대로 근사한 커스텀 구현.
 *
 * Material3 [androidx.compose.material3.Switch] 는 미체크(16dp) → 체크(24dp) 로 thumb 이 확대되는
 * 애니메이션이 내장되어 있고 CMP 버전에 따라 `thumbContent` 파라미터로 이 애니메이션을 억제할 수 없다.
 * 앱 톤을 iOS 로 통일하려면 크기 변화가 없어야 해서 트랙/썸 을 직접 [Box] 로 그린다.
 *
 * 사용자 토글 시 selection 햅틱이 자동으로 발화된다 — 호출부에서 별도로 fire 할 필요 없음.
 *
 * @param checked 현재 on/off 상태.
 * @param onCheckedChange 사용자가 토글했을 때 호출. 프로그램적 변경 시엔 발화하지 않음.
 * @param modifier 스위치 컨테이너에 적용할 [Modifier].
 * @param enabled 상호작용 가능 여부. false 면 회색 톤(alpha 0.5) 으로 표시되며 탭 무시.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) PrimaryBlue else Separator,
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) ThumbTravel else 0.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 800f),
        label = "switchThumb",
    )
    val fireHaptic = rememberSelectionHaptic()
    Box(
        modifier = modifier
            .size(width = TrackWidth, height = TrackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = { new ->
                    fireHaptic()
                    onCheckedChange(new)
                },
            )
            .alpha(if (enabled) 1f else 0.5f)
            .padding(ThumbInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(ThumbSize)
                .clip(CircleShape)
                .background(SurfaceCard),
        )
    }
}

// ---------- Previews ----------

@Preview
@Composable
private fun AppSwitchPreview_States() {
    var on by remember { mutableStateOf(true) }
    var off by remember { mutableStateOf(false) }
    ProvidePretendard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceGray)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppSwitch(checked = on, onCheckedChange = { on = it })
            AppSwitch(checked = off, onCheckedChange = { off = it })
            AppSwitch(checked = true, onCheckedChange = {}, enabled = false)
            AppSwitch(checked = false, onCheckedChange = {}, enabled = false)
        }
    }
}
