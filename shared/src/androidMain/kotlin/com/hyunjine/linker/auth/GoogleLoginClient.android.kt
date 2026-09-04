package com.hyunjine.linker.auth

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.hyunjine.linker.data.Secrets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Android Google Sign-In (Credential Manager 기반).
 *
 * - `GetGoogleIdOption` 에 서버 Client ID ([Secrets.GoogleWebClientId]) 를 넘겨야 Google 이
 *   서버가 검증 가능한 id_token 을 발급. Web Client ID 는 Google Cloud Console → OAuth 2.0
 *   Client IDs 에서 "Web application" 타입으로 생성.
 * - nonce 는 raw 랜덤 32바이트 → SHA256 해싱 → hex 로 Google 에 전달. Supabase 는 raw nonce
 *   를 다시 SHA256 해 id_token 의 nonce claim 과 매칭.
 * - `setAutoSelectEnabled(false) + setFilterByAuthorizedAccounts(false)` 로 항상 계정 선택
 *   시트를 띄운다. 첫 로그인 UX 확보.
 */
actual class GoogleLoginClient(private val context: Context) {
    actual suspend fun login(): GoogleLoginResult {
        val serverClientId = Secrets.GoogleWebClientId
        if (serverClientId.isBlank()) {
            return GoogleLoginResult.Failure(
                "GoogleWebClientId 미설정 — local.properties 에 google.web.client.id 추가",
            )
        }
        val rawNonce = generateRawNonce()
        val hashedNonce = sha256Hex(rawNonce)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setNonce(hashedNonce)
            .setFilterByAuthorizedAccounts(false)   // 이 앱과 이전 연동 여부 무관하게 계정 선택 시트 노출
            .setAutoSelectEnabled(false)            // 자동 선택 금지 — 명시적 계정 선택 요구
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return runCatching {
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            when (credential.type) {
                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    val googleCredential = runCatching { GoogleIdTokenCredential.createFrom(credential.data) }
                        .getOrElse { e ->
                            Log.w(TAG, "GoogleIdTokenCredential parse 실패", e)
                            return GoogleLoginResult.Failure("Google credential parse 실패: ${e::class.simpleName}")
                        }
                    val idToken = googleCredential.idToken
                    if (idToken.isBlank()) {
                        GoogleLoginResult.Failure("id_token empty")
                    } else {
                        Log.i(TAG, "ok — idToken=${idToken.take(24)}… email=${googleCredential.id}")
                        GoogleLoginResult.Success(idToken = idToken, rawNonce = rawNonce)
                    }
                }
                else -> GoogleLoginResult.Failure("unexpected credential type: ${credential.type}")
            }
        }.getOrElse { e ->
            when (e) {
                is GetCredentialCancellationException -> {
                    Log.i(TAG, "cancelled")
                    GoogleLoginResult.Cancelled
                }
                is GoogleIdTokenParsingException -> {
                    Log.w(TAG, "id_token parse 실패", e)
                    GoogleLoginResult.Failure("id_token parse 실패: ${e.message}")
                }
                else -> {
                    Log.w(TAG, "GetCredential 실패", e)
                    GoogleLoginResult.Failure(e.message ?: e::class.simpleName ?: "unknown")
                }
            }
        }
    }
}

@Composable
actual fun rememberGoogleLoginClient(): GoogleLoginClient {
    val context = LocalContext.current
    return remember(context) { GoogleLoginClient(context) }
}

private const val TAG = "GoogleLogin"

/** 32바이트 랜덤 → hex 문자열. Google 로 넘길 SHA256 nonce 생성에 사용. */
private fun generateRawNonce(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

/** raw 문자열 → SHA256 hex. Google 요청 nonce 필드에 넘기는 값. */
private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
