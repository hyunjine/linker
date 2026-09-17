package com.hyunjine.linker.adminweb.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.adminweb.auth.AdminAuthController
import com.hyunjine.linker.adminweb.auth.LoginReason
import com.hyunjine.linker.adminweb.auth.SignInFailure
import com.hyunjine.linker.adminweb.auth.SignInResult
import com.hyunjine.linker.adminweb.ui.AdminColors
import kotlinx.coroutines.launch

/**
 * 관리자 로그인 화면.
 *
 * Figma `4010:63060` 를 기준으로 상단 브랜딩 바 · 중앙 420 wide 카드 · 자동 로그인 체크박스 ·
 * primary pill 로그인 버튼 순서로 조립된다. 실패 사유는 카드 하단에 인라인 붉은 텍스트로만
 * 표시하고 스낵바를 쓰지 않는다 (`docs/261-admin-push-console.md` §3.0).
 *
 * @param controller 세션 · 로그인 액션을 담당하는 [AdminAuthController]. 성공 시 이 컴포저블은
 *   [onLoginSuccess] 를 호출하고, 실제 화면 전환은 상위 트리 (App) 가 담당한다.
 * @param initialReason 진입 시 카드 하단에 표시할 안내 사유. `LoginReason.NotAdmin` 이면 관리자
 *   자격이 없어 자동 로그아웃된 상태임을 사용자에게 알린다.
 * @param onLoginSuccess 로그인 · 화이트리스트 검증까지 성공했을 때 호출. 파라미터는 발급된 uid.
 */
@Composable
fun LoginScreen(
    controller: AdminAuthController,
    initialReason: LoginReason,
    onLoginSuccess: (uid: String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(true) }
    var signingIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 진입 시 컨트롤러가 지시한 안내 사유가 있으면 초기 에러 문구로 노출.
    LaunchedEffect(initialReason) {
        errorMessage = when (initialReason) {
            LoginReason.NotAdmin -> "관리자 권한이 없는 계정이에요."
            LoginReason.None -> null
        }
    }

    val submitEnabled by remember {
        derivedStateOf { email.isNotBlank() && password.isNotEmpty() && !signingIn }
    }

    val submit: () -> Unit = submit@{
        if (!submitEnabled) return@submit
        signingIn = true
        errorMessage = null
        scope.launch {
            val result = controller.signIn(email = email, password = password, rememberMe = rememberMe)
            signingIn = false
            when (result) {
                is SignInResult.Success -> onLoginSuccess(result.uid)
                is SignInResult.Failure -> errorMessage = when (result.reason) {
                    SignInFailure.InvalidCredentials -> "이메일 또는 비밀번호가 올바르지 않아요."
                    SignInFailure.NotAdmin -> "관리자 권한이 없는 계정이에요."
                    SignInFailure.Unknown -> result.message.ifBlank { "로그인에 실패했어요. 잠시 후 다시 시도해주세요." }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminColors.Background),
    ) {
        LoginTopBar()
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LoginCard(
                email = email,
                onEmailChange = { email = it },
                password = password,
                onPasswordChange = { password = it },
                rememberMe = rememberMe,
                onRememberMeChange = { rememberMe = it },
                signingIn = signingIn,
                submitEnabled = submitEnabled,
                onSubmit = submit,
                errorMessage = errorMessage,
            )
        }
    }
}

/**
 * 상단 브랜딩 바. 좌측에 `Linker · 관리자 콘솔` 라벨만 두고 우측 액션은 없다.
 */
@Composable
private fun LoginTopBar() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AdminColors.Background)
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Linker · 관리자 콘솔",
                color = AdminColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AdminColors.TopBarDivider),
        )
    }
}

/**
 * 로그인 카드. 420 wide · radius 20 · 소프트 섀도우. 내부는 제목 · 서브 · 이메일 · 비밀번호 ·
 * 자동 로그인 · 버튼 · 에러 순서로 20dp 간격으로 스택된다.
 *
 * @param onSubmit 로그인 버튼 탭 또는 필드 Enter (IME Done) 시 호출.
 */
@Composable
private fun LoginCard(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    rememberMe: Boolean,
    onRememberMeChange: (Boolean) -> Unit,
    signingIn: Boolean,
    submitEnabled: Boolean,
    onSubmit: () -> Unit,
    errorMessage: String?,
) {
    Column(
        modifier = Modifier
            .width(420.dp)
            .shadow(
                elevation = 32.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = AdminColors.CardShadow,
                spotColor = AdminColors.CardShadow,
            )
            .clip(RoundedCornerShape(20.dp))
            .background(AdminColors.CardBackground)
            .padding(PaddingValues(start = 32.dp, top = 36.dp, end = 32.dp, bottom = 32.dp)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "관리자 로그인",
                color = AdminColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "허용된 계정만 접근할 수 있어요",
                color = AdminColors.TextSubtle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            )
        }

        LoginField(
            label = "이메일",
            value = email,
            onValueChange = onEmailChange,
            placeholder = "admin@linker.app",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            visualPassword = false,
            enabled = !signingIn,
            onImeAction = { /* 이메일 필드에서는 이동만 유도 (별도 focus 이동은 브라우저 기본 흐름 활용) */ },
        )

        LoginField(
            label = "비밀번호",
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "비밀번호",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            visualPassword = true,
            enabled = !signingIn,
            onImeAction = { if (submitEnabled) onSubmit() },
        )

        RememberCheckbox(
            checked = rememberMe,
            onCheckedChange = onRememberMeChange,
            enabled = !signingIn,
        )

        LoginButton(
            enabled = submitEnabled,
            loading = signingIn,
            onClick = onSubmit,
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = AdminColors.Error,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

/**
 * 로그인 카드 내부에서 반복 사용되는 라벨 + 인풋 조합.
 *
 * @param onImeAction IME Done · Next 등 액션 키 입력 시 호출. 비밀번호 필드에서 Enter 로 즉시
 *   로그인을 트리거하기 위해 열어둔다.
 */
@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    visualPassword: Boolean,
    enabled: Boolean,
    onImeAction: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = AdminColors.TextLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AdminColors.FieldBackground)
                .padding(PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 14.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(
                    color = AdminColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                ),
                cursorBrush = SolidColor(AdminColors.BrandBlue),
                visualTransformation = if (visualPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onImeAction() },
                    onNext = { onImeAction() },
                    onGo = { onImeAction() },
                    onSend = { onImeAction() },
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = AdminColors.PlaceholderText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

/**
 * 자동 로그인 행. 16×16 사각 체크박스 + `자동 로그인` 라벨. 행 전체를 클릭 대상으로 삼는다.
 *
 * @param checked 현재 체크 상태.
 * @param onCheckedChange 상태 변경 콜백.
 * @param enabled `false` 면 진행 중이라 입력을 막는다.
 */
@Composable
private fun RememberCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .then(
                    if (checked) {
                        Modifier.background(AdminColors.BrandBlue)
                    } else {
                        Modifier
                            .background(AdminColors.CardBackground)
                            .border(width = 1.5.dp, color = AdminColors.CheckboxStroke, shape = RoundedCornerShape(4.dp))
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                // 얇은 체크 표시. SF Symbols 없이 유니코드 문자로 렌더 (wasmJs 는 Compose 아이콘 세트가
                // 제한적이라 텍스트 글리프가 가장 단순 · 폰트 폴백에 안정적).
                Text(
                    text = "✓",
                    color = AdminColors.OnBrand,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "자동 로그인",
            color = AdminColors.TextLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 카드 폭을 채우는 primary pill 버튼. 진행 중일 때는 인라인 스피너를 표시.
 *
 * @param enabled `email.isNotBlank() && password.isNotEmpty() && !signingIn` 조건이 만족될 때만 true.
 * @param loading `true` 면 라벨 대신 흰색 스피너를 렌더.
 */
@Composable
private fun LoginButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (enabled) AdminColors.BrandBlue else AdminColors.BrandBlue.copy(alpha = 0.4f))
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = AdminColors.OnBrand,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = "로그인",
                color = AdminColors.OnBrand,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
