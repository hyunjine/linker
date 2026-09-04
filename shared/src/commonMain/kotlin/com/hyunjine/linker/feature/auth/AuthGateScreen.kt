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
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_apple_logo
import linker.shared.generated.resources.ic_couple_rings
import linker.shared.generated.resources.ic_google_g
import linker.shared.generated.resources.ic_kakao_bubble
import org.jetbrains.compose.resources.DrawableResource
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
    showAppleLogin: Boolean = false,
    onAppleLoginClick: () -> Unit = {},
    onGoogleLoginClick: () -> Unit = {},
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
                text = "현진이랑 민교",
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

        // ── 소셜 로그인 버튼들 (login 에만, fade + 약간 지연) ──
        // Apple 은 iOS 만 노출 (showAppleLogin=true). 카카오는 Supabase 의
        // provider_email_needs_verification 이슈 해결 전까지 임시 숨김 (#179 참조).
        AnimatedVisibility(
            visible = mode == AuthGateMode.Login,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = kakaoBtnTop, start = 20.dp, end = 20.dp),
            enter = fadeIn(tween(durationMillis = 400, delayMillis = 250)),
            exit = fadeOut(tween(200)),
        ) {
            androidx.compose.foundation.layout.Column {
                // TODO(#179): Kakao email_verified: false 이슈 해결 후 다시 노출.
                //   현재 Kakao 는 콘솔에서 이메일 항목을 "사용 안 함" 처리했음에도 id_token 에
                //   email_verified=false 를 강제로 넣어 보냄. Supabase 가 이걸 보고 422 (신규가입 거부).
                //   대응 옵션: (a) 사용자 카카오 계정 이메일 완전 인증 (b) Supabase Auth Hook
                //   으로 email_verified override (Pro Plan 필요) (c) Supabase 지원팀 문의.
                // KakaoLoginButton(onClick = onKakaoLoginClick)
                // Spacer(Modifier.height(12.dp))
                if (showAppleLogin) {
                    AppleLoginButton(onClick = onAppleLoginClick)
                    Spacer(Modifier.height(12.dp))
                }
                GoogleLoginButton(onClick = onGoogleLoginClick)
            }
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

/**
 * 소셜 로그인 프로바이더별 시각 스펙. 각 요소가 버튼의 모든 시각 파라미터를 갖고 있어
 * [SocialLoginButton] 은 이 enum 만 받아 렌더한다.
 *
 * 새 프로바이더 추가 시 여기에만 요소 하나 추가하면 됨 (컴포넌트 시그니처 변경 없음).
 *
 * @property text 버튼 라벨.
 * @property iconResId 좌측 아이콘 drawable. 18dp 정사각으로 렌더.
 * @property backgroundColor 버튼 배경색.
 * @property strokeWidth 테두리 두께. 0.dp 면 테두리 없음.
 * @property strokeColor 테두리 색상 ([strokeWidth] 이 0.dp 초과일 때만 사용).
 * @property textColor 라벨 색상.
 * @property iconTint 아이콘 tint. null 이면 원본 컬러 유지 (Google 4색 로고용).
 *  Kakao·Apple 처럼 단색 로고를 배경에 맞춰 다시 칠할 땐 값을 지정.
 */
private enum class SocialLoginProvider(
    val text: String,
    val iconResId: DrawableResource,
    val backgroundColor: Color,
    val strokeWidth: Dp,
    val strokeColor: Color,
    val textColor: Color,
    val iconTint: Color?,
) {
    KAKAO(
        text = "카카오로 시작하기",
        iconResId = Res.drawable.ic_kakao_bubble,
        backgroundColor = KakaoYellow,
        strokeWidth = 0.dp,
        strokeColor = Color.Transparent,
        textColor = KakaoLabel,
        iconTint = KakaoLabel,
    ),
    APPLE(
        text = "애플로 시작하기",
        iconResId = Res.drawable.ic_apple_logo,
        backgroundColor = Color.Black,
        strokeWidth = 0.dp,
        strokeColor = Color.Transparent,
        textColor = Color.White,
        iconTint = Color.White,
    ),
    GOOGLE(
        text = "구글로 시작하기",
        iconResId = Res.drawable.ic_google_g,
        backgroundColor = Color.White,
        strokeWidth = 1.dp,
        strokeColor = Color(0xFFBBBBBB),
        textColor = Color.Black,
        iconTint = null,   // G 로고 원본 4색 유지
    ),
}

/**
 * 소셜 로그인 공통 버튼. 시각 스펙은 [provider] 하나에 모두 담겨있다.
 * Figma 디자인 3489:77567 기준 — height 50dp · rounded 14dp · icon 18dp · text 17sp SemiBold.
 */
@Composable
private fun SocialLoginButton(
    provider: SocialLoginProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(provider.backgroundColor)
            .then(
                if (provider.strokeWidth > 0.dp) {
                    Modifier.border(provider.strokeWidth, provider.strokeColor, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(provider.iconResId),
            contentDescription = null,
            colorFilter = provider.iconTint?.let { ColorFilter.tint(it) },
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = provider.text,
            style = TextStyle(
                color = provider.textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
    }
}

@Composable
private fun KakaoLoginButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    SocialLoginButton(SocialLoginProvider.KAKAO, onClick, modifier)

@Composable
private fun AppleLoginButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    SocialLoginButton(SocialLoginProvider.APPLE, onClick, modifier)

@Composable
private fun GoogleLoginButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    SocialLoginButton(SocialLoginProvider.GOOGLE, onClick, modifier)

@Preview
@Composable
private fun AuthGateSplashPreview() {
    LinkerTheme { AuthGateScreen(mode = AuthGateMode.Splash) }
}

/** 로그인 화면 (Android · 3개 소셜 버튼 모두 노출). */
@Preview
@Composable
private fun AuthGateLoginPreview() {
    LinkerTheme {
        AuthGateScreen(
            mode = AuthGateMode.Login,
            showAppleLogin = true,   // iOS 는 platform actual 이 true. preview 는 검토용으로 강제.
        )
    }
}

/** 로그인 화면 (Android · Apple 숨김 케이스). */
@Preview
@Composable
private fun AuthGateLoginAndroidPreview() {
    LinkerTheme {
        AuthGateScreen(
            mode = AuthGateMode.Login,
            showAppleLogin = false,
        )
    }
}

/** 로그인 화면 (Debug 빌드 · 하단 테스트 로그인 진입 노출). */
@Preview
@Composable
private fun AuthGateLoginDebugPreview() {
    LinkerTheme {
        AuthGateScreen(
            mode = AuthGateMode.Login,
            showAppleLogin = true,
            showDebugLogin = true,
        )
    }
}

// ────────── 개별 소셜 로그인 버튼 프리뷰 ──────────
// 각 프로바이더 스펙 (SocialLoginProvider enum) 을 개별 검토할 때 사용.

@Preview
@Composable
private fun KakaoLoginButtonPreview() {
    LinkerTheme {
        Box(modifier = Modifier.background(Background).padding(20.dp)) {
            KakaoLoginButton(onClick = {})
        }
    }
}

@Preview
@Composable
private fun AppleLoginButtonPreview() {
    LinkerTheme {
        Box(modifier = Modifier.background(Background).padding(20.dp)) {
            AppleLoginButton(onClick = {})
        }
    }
}

@Preview
@Composable
private fun GoogleLoginButtonPreview() {
    LinkerTheme {
        Box(modifier = Modifier.background(Background).padding(20.dp)) {
            GoogleLoginButton(onClick = {})
        }
    }
}
