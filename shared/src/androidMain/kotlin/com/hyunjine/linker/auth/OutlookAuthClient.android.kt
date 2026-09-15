package com.hyunjine.linker.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.hyunjine.linker.shared.R
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalServiceException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android MSAL 기반 Outlook 로그인.
 *
 * `res/raw/msal_config.json` 을 로드하는 `SingleAccountPublicClientApplication` 인스턴스는
 * 최초 접근 시 lazy 생성 후 프로세스 lifetime 동안 재사용 (SDK 권장). refresh_token 은
 * EncryptedSharedPreferences 에 저장돼 앱 재시작에도 자동 로그인 유지.
 *
 * `signIn` (대화형) 은 Activity 컨텍스트가 필요해 [context] 를 Activity 로 언랩 후 사용.
 * 앱은 항상 Activity 하위 Compose 트리 (`ComponentActivity` → `setContent`) 에서 실행되므로
 * unwrap 은 실패하지 않는다.
 */
actual class OutlookAuthClient(private val context: Context) {

    /** Graph API 접근 delegated scopes. Azure 앱 등록 permissions 와 정확히 일치해야 한다. */
    private val scopes: List<String> = listOf(
        "Calendars.ReadWrite",
        "User.Read",
        // offline_access 는 authorities 설정에서 자동 부여 — 명시적 request scope 에는 불필요.
    )

    actual suspend fun login(): OutlookAuthResult {
        val app = getOrCreateApp() ?: return OutlookAuthResult.Failure("MSAL init 실패")
        val activity = context.findActivity()
            ?: return OutlookAuthResult.Failure("Activity 컨텍스트 필요 (Compose 트리 위치 확인)")
        val existing = getCurrentAccountSuspend(app)
        // 이미 로그인된 계정이 있으면 계정 선택 시트 대신 조용히 토큰만 새로 발급 (SDK 자동 refresh).
        if (existing != null) {
            val silent = acquireTokenSilent(app, existing)
            if (silent != null) return silent.toSuccess()
            // silent 실패 (refresh_token 만료 등) → 대화형 fallback.
        }
        return runCatching {
            suspendCancellableCoroutine { cont ->
                val params = SignInParameters.builder()
                    .withActivity(activity)
                    .withScopes(scopes)
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(result: IAuthenticationResult) {
                            cont.resume(result.toSuccess())
                        }
                        override fun onCancel() {
                            cont.resume(OutlookAuthResult.Cancelled)
                        }
                        override fun onError(exception: MsalException) {
                            cont.resume(OutlookAuthResult.Failure(exception.describe()))
                        }
                    })
                    .build()
                app.signIn(params)
            }
        }.getOrElse { e ->
            Log.w(TAG, "signIn 실패", e)
            OutlookAuthResult.Failure(e.message ?: e::class.simpleName ?: "unknown")
        }
    }

    actual suspend fun signOut() {
        val app = getOrCreateApp() ?: return
        runCatching {
            suspendCancellableCoroutine<Unit> { cont ->
                app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() { cont.resume(Unit) }
                    override fun onError(exception: MsalException) {
                        Log.w(TAG, "signOut 실패", exception)
                        cont.resume(Unit) // 실패해도 앱 상태는 로그아웃으로 취급
                    }
                })
            }
        }
    }

    actual suspend fun currentAccount(): OutlookAccount? {
        val app = getOrCreateApp() ?: return null
        return getCurrentAccountSuspend(app)?.toAccount()
    }

    actual suspend fun accessToken(): String? {
        val app = getOrCreateApp() ?: return null
        val account = getCurrentAccountSuspend(app) ?: return null
        return acquireTokenSilent(app, account)?.accessToken
    }

    // ────────── internals ──────────

    /**
     * SDK 인스턴스는 프로세스 전역 싱글턴. `PublicClientApplication` 팩토리가 내부적으로
     * config 파싱 · 캐시 초기화를 수행하므로 재호출 비용이 크다. companion 캐시에 저장 후 재사용.
     */
    private suspend fun getOrCreateApp(): ISingleAccountPublicClientApplication? {
        cachedApp?.let { return it }
        return runCatching {
            suspendCancellableCoroutine<ISingleAccountPublicClientApplication?> { cont ->
                PublicClientApplication.createSingleAccountPublicClientApplication(
                    context.applicationContext,
                    R.raw.msal_config,
                    object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                        override fun onCreated(application: ISingleAccountPublicClientApplication) {
                            cachedApp = application
                            cont.resume(application)
                        }
                        override fun onError(exception: MsalException) {
                            Log.w(TAG, "MSAL init 실패", exception)
                            cont.resume(null)
                        }
                    },
                )
            }
        }.getOrElse { e ->
            Log.w(TAG, "MSAL init 예외", e); null
        }
    }

    private suspend fun getCurrentAccountSuspend(
        app: ISingleAccountPublicClientApplication,
    ): IAccount? = suspendCancellableCoroutine { cont ->
        app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: IAccount?) { cont.resume(activeAccount) }
            override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                // 다른 계정으로 바뀐 경우 (외부에서 signOut 후 다른 계정 로그인 등). 최신값만 사용.
                cont.resume(currentAccount)
            }
            override fun onError(exception: MsalException) {
                Log.w(TAG, "getCurrentAccount 실패", exception); cont.resume(null)
            }
        })
    }

    /**
     * refresh_token 으로 access_token 발급 시도. 성공 시 [IAuthenticationResult] 반환,
     * refresh_token 도 만료됐거나 실패면 null (호출자는 대화형 로그인으로 fallback).
     */
    private suspend fun acquireTokenSilent(
        app: ISingleAccountPublicClientApplication,
        account: IAccount,
    ): IAuthenticationResult? = suspendCancellableCoroutine { cont ->
        val params = com.microsoft.identity.client.AcquireTokenSilentParameters.Builder()
            .forAccount(account)
            .fromAuthority(account.authority)
            .withScopes(scopes)
            .withCallback(object : com.microsoft.identity.client.SilentAuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    cont.resume(authenticationResult)
                }
                override fun onError(exception: MsalException) {
                    if (exception is MsalUiRequiredException) {
                        Log.i(TAG, "silent 실패 (UI 필요) — 대화형 로그인으로 fallback")
                    } else {
                        Log.w(TAG, "silent 실패", exception)
                    }
                    cont.resume(null)
                }
            })
            .build()
        app.acquireTokenSilentAsync(params)
    }

    companion object {
        @Volatile private var cachedApp: ISingleAccountPublicClientApplication? = null
    }
}

@Composable
actual fun rememberOutlookAuthClient(): OutlookAuthClient {
    val context = LocalContext.current
    return remember(context) { OutlookAuthClient(context) }
}

private const val TAG = "OutlookAuth"

/** Compose 의 LocalContext 는 ContextWrapper 체인일 수 있어 Activity 를 찾을 때까지 언랩. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun IAuthenticationResult.toSuccess(): OutlookAuthResult.Success {
    val claims = account.claims.orEmpty()
    val email = (claims["preferred_username"] as? String)
        ?: (claims["email"] as? String)
        ?: account.username
    val displayName = claims["name"] as? String
    return OutlookAuthResult.Success(
        accountId = account.id,
        email = email,
        displayName = displayName,
        accessToken = accessToken,
    )
}

private fun IAccount.toAccount(): OutlookAccount {
    val claims = claims.orEmpty()
    val email = (claims["preferred_username"] as? String)
        ?: (claims["email"] as? String)
        ?: username
    val displayName = claims["name"] as? String
    return OutlookAccount(accountId = id, email = email, displayName = displayName)
}

/** MSAL 예외를 개발자 로그용 짧은 문자열로. UI 노출 텍스트가 아니라 진단 용. */
private fun MsalException.describe(): String = when (this) {
    is MsalUiRequiredException -> "ui_required: $errorCode"
    is MsalServiceException -> "service: $errorCode ${message ?: ""}"
    is MsalClientException -> "client: $errorCode ${message ?: ""}"
    else -> "${this::class.simpleName}: $errorCode ${message ?: ""}"
}
