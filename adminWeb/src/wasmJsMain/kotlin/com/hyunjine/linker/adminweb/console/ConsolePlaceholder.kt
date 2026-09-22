package com.hyunjine.linker.adminweb.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.adminweb.ui.AdminColors

/**
 * 발송 콘솔 화면의 임시 stub.
 *
 * #281 이 실제 발송 콘솔 UI 로 이 파일을 교체할 예정이다. 여기서는 라우팅이 로그인 성공 이후
 * 콘솔 화면으로 잘 넘어가는지만 시각적으로 확인할 수 있는 최소 문구를 렌더한다.
 */
@Composable
fun ConsolePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "발송 콘솔 · TODO #281",
            color = AdminColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
