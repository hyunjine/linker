package com.hyunjine.linker.adminweb.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.adminweb.auth.AdminAuthController
import com.hyunjine.linker.adminweb.ui.Colors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 관리자 푸시 발송 콘솔의 최상위 화면.
 *
 * 상단바 (`현진이랑민교 · 관리자 콘솔` + 로그아웃) 아래에 좌·우 두 패널을 배치한다.
 * 좌 패널은 계정 검색 + 리스트 ([AccountList]), 우 패널은 메시지 폼 ([MessageForm]).
 *
 * 이 컴포저블은 라우터 (`#280`) 가 로그인 성공 후 렌더하는 진입점이다. 라우터 도입 전에는
 * `App.kt` 의 route === Console 브랜치에서 이 컴포저블을 호출하도록 배선한다. 라우터 구조가
 * 확정되면 `ConsolePlaceholder` 를 이 컴포저블로 교체하면 된다 (`#280` 협업 지점).
 *
 * @param authController 로그아웃 액션 호출용 인증 컨트롤러.
 * @param onSignOut 로그아웃이 완료됐을 때 상위 라우터가 로그인 화면으로 리다이렉트할 훅.
 *   `null` 이면 컨트롤러의 [AdminAuthController.signOut] 만 태우고 라우팅은 `sessionStatus`
 *   변화로 자연스럽게 발생하는 방식에 맡긴다.
 * @param modifier 외부에서 크기/배경을 조정할 여지.
 */
@Composable
fun ConsoleScreen(
    authController: AdminAuthController,
    onSignOut: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state = remember { ConsoleState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { state.refresh() }

    // 성공 스낵바는 3.5초 후 자동 dismiss. 실패는 사용자가 명시적으로 확인할 수 있도록 5초.
    LaunchedEffect(state.snackbar) {
        val current = state.snackbar ?: return@LaunchedEffect
        val duration = when (current.kind) {
            SnackbarMessage.Kind.Success -> 3500L
            SnackbarMessage.Kind.Error -> 5000L
        }
        delay(duration)
        state.consumeSnackbar()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Colors.Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                onSignOut = {
                    coroutineScope.launch {
                        authController.signOut()
                        onSignOut?.invoke()
                    }
                },
            )
            ConsoleBody(state = state)
        }

        SnackbarHost(
            snackbar = state.snackbar,
            onDismiss = { state.consumeSnackbar() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        )
    }
}

/**
 * 상단 브랜드 바 + 우측 로그아웃.
 *
 * @param onSignOut 로그아웃 텍스트 탭 시 호출.
 */
@Composable
private fun TopBar(onSignOut: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .background(Colors.Surface)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val title = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = Colors.PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                ) {
                    append("현진이랑민교")
                }
                withStyle(SpanStyle(color = Colors.TextPrimary.copy(alpha = 0.7f))) {
                    append("  ·  ")
                }
                withStyle(
                    SpanStyle(
                        color = Colors.TextPrimary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    ),
                ) {
                    append("관리자 콘솔")
                }
            }
            Text(text = title)
            Spacer(Modifier.weight(1f))
            SignOutButton(onClick = onSignOut)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Colors.Divider),
        )
    }
}

/** 로그아웃 텍스트 버튼 — 회색 톤. 확인 다이얼로그 없음 (§6.4 결정). */
@Composable
private fun SignOutButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "로그아웃",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Colors.TextSecondary,
        )
    }
}

/**
 * 좌 · 우 두 패널을 1440 기준 폭에 맞춰 배치한다. 뷰포트가 좁아지면 각 패널이 비율로 축소.
 */
@Composable
private fun ConsoleBody(state: ConsoleState) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        val available = maxWidth
        // Figma 기준 420 : 880 = 좌 : 우. 총 1300 (마진 제외) 에 맞춰 비율 유지.
        val leftFraction = 420f / (420f + 880f)
        val gap = 20.dp

        val leftWidth = available * leftFraction - gap / 2
        val rightWidth = available * (1f - leftFraction) - gap / 2

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            AccountList(
                state = state,
                modifier = Modifier
                    .width(leftWidth)
                    .widthIn(min = 300.dp)
                    .fillMaxHeight(),
            )
            MessageForm(
                state = state,
                modifier = Modifier
                    .width(rightWidth)
                    .widthIn(min = 360.dp)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * 하단 중앙 스낵바 호스트. 성공 · 실패에 따라 배경색을 다르게 하고, 사용자가 탭하면 즉시 dismiss.
 *
 * @param snackbar 현재 노출할 메시지. `null` 이면 아무것도 그리지 않는다.
 * @param onDismiss 사용자가 스낵바를 탭 했을 때 호출.
 * @param modifier 외부 배치 modifier.
 */
@Composable
private fun SnackbarHost(
    snackbar: SnackbarMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = snackbar != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val message = snackbar ?: return@AnimatedVisibility
        val bg = when (message.kind) {
            SnackbarMessage.Kind.Success -> Colors.ToastSuccess
            SnackbarMessage.Kind.Error -> Colors.ToastError
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = message.text,
                color = Colors.OnPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

