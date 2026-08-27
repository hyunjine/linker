package com.hyunjine.linker.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.theme.Background
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import androidx.compose.ui.tooling.preview.Preview
import com.hyunjine.linker.ui.theme.LogoGradient
import com.hyunjine.linker.ui.theme.PrimaryBlue
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.TextPrimary

/**
 * 앱 시작 시 세션 storage 복원 중 (`SessionStatus.Initializing`) 노출되는 스플래시.
 * 부트스트랩 라우팅이 목적지 (Login / ProfileSetup / CoupleLink / Main) 를 결정하면 자동 대체.
 */
@Composable
fun SplashScreen() {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(LogoGradient),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "링커",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                    ),
                )
            }
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = PrimaryBlue,
                strokeWidth = 2.dp,
            )
            Text(
                text = "잠시만요…",
                style = TextStyle(
                    color = TextPrimary.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontFamily = font,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun SplashScreenPreview() {
    ProvidePretendard { SplashScreen() }
}
