package com.hyunjine.linker.auth

import android.content.Context
import android.util.Log
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
 * ([initKakaoSdk] · [com.hyunjine.linker.LinkerApplication]). 여기선 UserApiClient 만 호출.
 *
 * 카카오톡이 설치돼 있으면 톡으로 로그인, 아니면 카카오 계정 웹 로그인으로 폴백.
 * 사용자가 톡 로그인 창을 백버튼으로 닫은 경우도 [ClientErrorCause.Cancelled] 로 잡아 웹 로그인으로 폴백해서
 * "화면이 잠깐 열렸다 닫히는" 어색한 UX 를 방지한다.
 *
 * `scopes = listOf("openid")` 를 명시 — OIDC 활성화된 앱에서만 `token.idToken` 이 반환된다.
 * (콘솔 · 앱 설정 · 카카오 로그인 · OpenID Connect 활성화 ON 필요)
 */
actual class KakaoLoginClient(private val context: Context) {
    actual suspend fun logout() = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.logout { error ->
            if (error != null) Log.w(TAG, "logout error: $error") else Log.i(TAG, "logout ok")
            if (cont.isActive) cont.resume(Unit)
        }
    }

    actual suspend fun login(): KakaoLoginResult = suspendCancellableCoroutine { cont ->
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            val result = when {
                token != null -> {
                    logToken(token)
                    val idToken = token.idToken
                    if (idToken.isNullOrBlank()) {
                        KakaoLoginResult.Failure("id_token 없음 — 콘솔에서 OpenID Connect 활성화 확인")
                    } else {
                        KakaoLoginResult.Success(idToken = idToken)
                    }
                }
                error is ClientError && error.reason == ClientErrorCause.Cancelled ->
                    KakaoLoginResult.Cancelled
                error != null -> KakaoLoginResult.Failure(error.message ?: error::class.simpleName ?: "unknown")
                else -> KakaoLoginResult.Failure("no token, no error")
            }
            if (cont.isActive) cont.resume(result)
        }

        // OIDC 활성화는 카카오 콘솔 · 앱 설정 · 카카오 로그인 · OpenID Connect 활성화 ON 으로
        // 결정됨 (SDK 파라미터로 요청하는 게 아님). 활성화되면 token.idToken 자동 포함.
        val client = UserApiClient.instance
        if (client.isKakaoTalkLoginAvailable(context)) {
            client.loginWithKakaoTalk(context) { token, error ->
                if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                    callback(null, error)
                } else if (error != null) {
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

private const val TAG = "KakaoLogin"

private fun logToken(token: OAuthToken) {
    Log.i(TAG, "accessToken=${token.accessToken.take(12)}… idToken=${token.idToken?.take(24)}… scopes=${token.scopes}")
}
