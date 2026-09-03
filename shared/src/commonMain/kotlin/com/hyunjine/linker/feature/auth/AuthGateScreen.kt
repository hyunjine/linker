package com.hyunjine.linker.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.theme.Background
import com.hyunjine.linker.designsystem.theme.KakaoLabel
import com.hyunjine.linker.designsystem.theme.KakaoYellow
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_couple_rings
import linker.shared.generated.resources.ic_kakao_bubble
import org.jetbrains.compose.resources.painterResource

/**
 * 스플래시 · 로그인 을 하나의 컴포저블로 합쳐 로고 이동 애니메이션이 자연스럽게 이어지도록 함.
 *
 * - [mode] = [AuthGateMode.Splash]: 로고 하단·타이틀·부제 "커플 캘린더" 노출
 * - [mode] = [AuthGateMode.Login]: 로고 상단·타이틀·부제 사라지고 카카오 버튼 fade-in
 *
 * 두 상태 사이는 [animateDpAsState] · [AnimatedVisibility] 로 tween.
 * Figma 캔버스 402x852 기준 비율을 [BoxWithConstraints] 의 maxHeight 에 매핑해 세로 위치 계산.
 */
enum class AuthGateMode { Splash, Login }

private const val DESIGN_HEIGHT = 852f
private const val LOGO_SIZE_DP = 112
private const val TITLE_TOP_OFFSET_DP = 12 // 로고 아래 여백 (Figma: 460-320-112=28 · 로고 top→title top 148)

@Composable
fun AuthGateScreen(
    mode: AuthGateMode,
    onKakaoLoginClick: () -> Unit = {},
    showDebugLogin: Boolean = false,
    onDebugLoginClick: () -> Unit = {},
) {
    val font = LocalPretendardFontFamily.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val h = maxHeight
        // Figma 좌표를 화면 높이 비율로 환산. splash · login 두 상태 사이를 tween.
        val logoTopSplash = h * (320f / DESIGN_HEIGHT)   // ≈ 0.376
        val logoTopLogin = h * (167f / DESIGN_HEIGHT)    // ≈ 0.196
        val titleTopSplash = h * (460f / DESIGN_HEIGHT)  // ≈ 0.540
        val titleTopLogin = h * (307f / DESIGN_HEIGHT)   // ≈ 0.360
        val subtitleTop = h * (508f / DESIGN_HEIGHT)     // splash 에만 노출
        val kakaoBtnTop = h * (510f / DESIGN_HEIGHT)     // login 에만 노출

        val slideAnim = tween<androidx.compose.ui.unit.Dp>(
            durationMillis = 550,
            easing = FastOutSlowInEasing,
        )
        val logoTop by animateDpAsState(
            targetValue = if (mode == AuthGateMode.Login) logoTopLogin else logoTopSplash,
            animationSpec = slideAnim,
            label = "logoTop",
        )
        val titleTop by animateDpAsState(
            targetValue = if (mode == AuthGateMode.Login) titleTopLogin else titleTopSplash,
            animationSpec = slideAnim,
            label = "titleTop",
        )

        // ── 로고 (양쪽 상태 모두 노출, 위치만 이동) ──
        CoupleLogo(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = logoTop)
                .size(LOGO_SIZE_DP.dp),
        )

        // ── 타이틀 "현진이와 민교" (양쪽 상태 모두 노출, 위치만 이동) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = titleTop),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "현진이와 민교",
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = font,
                    letterSpacing = (-0.64).sp,
                ),
            )
        }

        // ── 부제 "커플 캘린더" (splash 에만, fade) ──
        AnimatedVisibility(
            visible = mode == AuthGateMode.Splash,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = subtitleTop),
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(250)),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "커플 캘린더",
                    style = TextStyle(
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontFamily = font,
                    ),
                )
            }
        }

        // ── 카카오 버튼 (login 에만, fade + 약간 지연) ──
        AnimatedVisibility(
            visible = mode == AuthGateMode.Login,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = kakaoBtnTop, start = 20.dp, end = 20.dp),
            enter = fadeIn(tween(durationMillis = 400, delayMillis = 250)),
            exit = fadeOut(tween(200)),
        ) {
            KakaoLoginButton(onClick = onKakaoLoginClick)
        }

        // ── 디버그 로그인 진입 (debug 빌드 · login 상태 에서만) ──
        AnimatedVisibility(
            visible = mode == AuthGateMode.Login && showDebugLogin,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            enter = fadeIn(tween(400, delayMillis = 400)),
            exit = fadeOut(tween(150)),
        ) {
            TextButton(onClick = onDebugLoginClick) {
                Text(
                    text = "테스트 계정으로 로그인",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = TextPrimary.copy(alpha = 0.6f),
                        fontFamily = font,
                    ),
                )
            }
        }
    }
}

/**
 * 파란색 그라디언트 원 안에 두 개의 흰색 링. SVG 배경 원은 컴포저블 [background] 로,
 * 링 두 개는 vector drawable 로 그린다.
 */
@Composable
fun CoupleLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    // Figma: #33A0FF → #008AFF (0.5) → #65B5FF, 대각선 (0,0)→(79,79)
                    colorStops = arrayOf(
                        0.0f to Color(0xFF33A0FF),
                        0.5f to Color(0xFF008AFF),
                        1.0f to Color(0xFF65B5FF),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_couple_rings),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun KakaoLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KakaoYellow)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_kakao_bubble),
            contentDescription = null,
            tint = KakaoLabel,
            modifier = Modifier.size(width = 20.dp, height = 18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "카카오로 시작하기",
            style = TextStyle(
                color = KakaoLabel,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
    }
}

@Preview
@Composable
private fun AuthGateSplashPreview() {
    ProvidePretendard { AuthGateScreen(mode = AuthGateMode.Splash) }
}

@Preview
@Composable
private fun AuthGateLoginPreview() {
    ProvidePretendard { AuthGateScreen(mode = AuthGateMode.Login) }
}
