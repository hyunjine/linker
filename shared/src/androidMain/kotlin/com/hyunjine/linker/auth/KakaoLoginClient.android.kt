package com.hyunjine.linker.auth

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android 카카오 로그인. `KakaoSdk.init` 은 앱 프로세스 시작 시 Application 에서 한 번 호출됐다고 가정
 * (LinkerApplication 참고). 여기선 UserApiClient 만 호출.
 *
 * 카카오톡이 설치돼 있으면 톡으로 로그인, 아니면 카카오 계정 웹 로그인으로 폴백.
 * 사용자가 톡 로그인 창을 백버튼으로 닫은 경우도 [ClientErrorCause.Cancelled] 로 잡아 웹 로그인으로 폴백해서
 * "화면이 잠깐 열렸다 닫히는" 어색한 UX 를 방지한다 (카카오 공식 권장 패턴).
 */
actual class KakaoLoginClient(private val context: Context) {
    actual suspend fun login(): KakaoLoginResult = suspendCancellableCoroutine { cont ->
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            val result = when {
                token != null -> KakaoLoginResult.Success(
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                )
                error is ClientError && error.reason == ClientErrorCause.Cancelled ->
                    KakaoLoginResult.Cancelled
                error != null -> KakaoLoginResult.Failure(error.message ?: error::class.simpleName ?: "unknown")
                else -> KakaoLoginResult.Failure("no token, no error")
            }
            if (cont.isActive) cont.resume(result)
        }

        val client = UserApiClient.instance
        if (client.isKakaoTalkLoginAvailable(context)) {
            client.loginWithKakaoTalk(context) { token, error ->
                if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                    // 톡 로그인 창을 뒤로가기로 닫은 경우 — 웹 로그인으로 폴백하지 않고 취소로 취급.
                    callback(null, error)
                } else if (error != null) {
                    // 톡 로그인 실패 (미로그인 상태 등) → 카카오 계정 웹 로그인 폴백
                    client.loginWithKakaoAccount(context, callback = callback)
                } else {
                    callback(token, null)
                }
            }
        } else {
            client.loginWithKakaoAccount(context, callback = callback)
        }
    }
}

@Composable
actual fun rememberKakaoLoginClient(): KakaoLoginClient {
    val context = LocalContext.current
    return remember(context) { KakaoLoginClient(context) }
}
