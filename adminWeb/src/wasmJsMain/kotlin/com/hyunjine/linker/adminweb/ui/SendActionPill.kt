package com.hyunjine.linker.adminweb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 관리자 콘솔의 primary 액션 pill.
 *
 * 앱의 [`SaveActionPill`](../../../../../../../shared/src/commonMain/kotlin/com/hyunjine/linker/designsystem/common/SheetToolbar.kt)
 * 와 시각·톤을 통일한다 (#269 로딩 처리 방식 그대로).
 * - 로딩 중에도 라벨 자리를 유지해 pill 폭이 튀지 않도록 텍스트를 투명으로 렌더하고 위에 스피너를 얹는다.
 * - disabled 는 `.alpha()` 없이 fill/text 컬러의 alpha 로 감쇠 — Compose skia iOS 이슈 회피 관례를 준용.
 *
 * `:adminWeb` 은 iOS 리퀴드 글래스 모디파이어가 없으므로 solid fill + rounded corner 로 대체.
 *
 * @param label 버튼 라벨 문자열 (예: "발송").
 * @param enabled 사용자가 탭할 수 있는지 여부. `false` 면 컬러가 감쇠되고 클릭 무시.
 * @param onClick 사용자가 탭 했을 때 실행. `enabled && !loading` 일 때만 호출됨.
 * @param modifier 외부에서 크기·패딩을 조정할 때 사용.
 * @param loading `true` 면 라벨 대신 인라인 스피너를 노출하고 탭을 무시한다.
 */
@Composable
fun SendActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val pillColor = if (enabled) Colors.PrimaryBlue else Colors.PrimaryBlue.copy(alpha = 0.5f)
    val textColor = when {
        loading -> Color.Transparent
        enabled -> Colors.OnPrimary
        else -> Colors.OnPrimary.copy(alpha = 0.5f)
    }
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(pillColor)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        // `LocalTextStyle.current.copy(...)` 로 감싸야 Pretendard fontFamily 가 유지된다.
        // 인라인 `TextStyle(...)` 는 fontFamily=null 을 강제해 한글이 tofu 로 렌더됨.
        Text(
            text = label,
            style = LocalTextStyle.current.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = textColor,
            ),
        )
        if (loading) {
            CircularProgressIndicator(
                color = Colors.OnPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
