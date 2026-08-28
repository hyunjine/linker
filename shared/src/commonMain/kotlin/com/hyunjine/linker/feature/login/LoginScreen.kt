package com.hyunjine.linker.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.theme.Background
import com.hyunjine.linker.designsystem.theme.KakaoLabel
import com.hyunjine.linker.designsystem.theme.KakaoYellow
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.LogoGradient
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.TextPrimary
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_kakao_bubble
import org.jetbrains.compose.resources.painterResource

// Figma 프레임 402x844 기준 비율.
// 세로: 위여백:히어로:중간여백:버튼:아래여백 = 189 : (intrinsic) : 174 : (intrinsic) : 279
// 가로 버튼: 좌:본체:우 = 20 : 362 : 20
@Composable
fun LoginScreen(
    onKakaoLoginClick: () -> Unit = {},
    showDebugLogin: Boolean = false,
    debugError: String? = null,
    onDebugLoginSubmit: (email: String, password: String) -> Unit = { _, _ -> },
    onDebugErrorDismiss: () -> Unit = {},
) {
    var debugSheetOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.fillMaxHeight().weight(189f))

        Hero()

        Spacer(Modifier.fillMaxHeight().weight(174f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.fillMaxWidth().weight(20f))
            KakaoLoginButton(
                onClick = onKakaoLoginClick,
                modifier = Modifier.weight(362f),
            )
            Spacer(Modifier.fillMaxWidth().weight(20f))
        }

        // DEBUG 빌드에서만 노출되는 테스트 계정 로그인 진입. Release 는 렌더 자체 안 됨.
        if (showDebugLogin) {
            Spacer(Modifier.size(12.dp))
            TextButton(onClick = { debugSheetOpen = true }) {
                Text(
                    text = "테스트 계정으로 로그인",
                    style = TextStyle(fontSize = 13.sp, color = TextPrimary.copy(alpha = 0.6f)),
                )
            }
        }

        Spacer(Modifier.fillMaxHeight().weight(279f))
    }

    if (debugSheetOpen) {
        DebugLoginSheet(
            error = debugError,
            onDismiss = {
                debugSheetOpen = false
                onDebugErrorDismiss()
            },
            onSubmit = { email, password ->
                onDebugLoginSubmit(email, password)
                debugSheetOpen = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugLoginSheet(
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (email: String, password: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "[DEBUG] 테스트 로그인",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Text(
                    text = error,
                    style = TextStyle(fontSize = 12.sp, color = Color(0xFFD03A3A)),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("취소") }
                Spacer(Modifier.size(8.dp))
                TextButton(
                    enabled = email.isNotBlank() && password.isNotBlank(),
                    onClick = { onSubmit(email, password) },
                ) { Text("로그인") }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun Hero(modifier: Modifier = Modifier) {
    val font = LocalPretendardFontFamily.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(LogoGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "현민",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = font,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        Text(
            text = "현진이와 민교",
            style = TextStyle(
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                letterSpacing = (-0.56).sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun KakaoLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    // 버튼 자체는 콘텐츠(아이콘 + 텍스트)를 감싸는 컨테이너. 내부 패딩·아이콘·텍스트 간격은
    // 카카오 가이드 고정 규격이라 dp 그대로 유지 (px-[20px] py-[15px], gap-[8px]).
    Row(
        modifier = modifier
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
fun LoginScreenPreview() {
    // Preview에서도 CompositionLocal이 필요하므로 여기서 감싸줍니다.
    ProvidePretendard {
        LoginScreen()
    }
}
