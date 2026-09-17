package com.hyunjine.linker.adminweb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 관리자 콘솔 루트 컴포저블.
 *
 * 이슈 #275 부트스트랩 스코프에서는 브라우저 렌더 파이프라인이 정상 동작하는지 확인하기
 * 위한 placeholder 만 렌더한다. 실제 로그인 · 발송 화면은 후속 이슈에서 이 자리에 조립.
 */
@Composable
fun App() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5FA)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Linker · 관리자 콘솔",
                color = Color(0xFF111111),
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
