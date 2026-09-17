package com.hyunjine.linker.adminweb.auth

import com.hyunjine.linker.adminweb.config.AdminWebConfig
import com.hyunjine.linker.adminweb.data.remote.AdminSupabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 관리자 콘솔의 화면 무관한 인증 상태 홀더 · 서비스.
 *
 * #280 (UI) 이 이 컨트롤러를 소비해 로그인 카드 · 콘솔 리다이렉트 · 세션 만료 스낵바를
 * 그리고, `signIn` / `signOut` 을 호출한다.
 *
 * 라이프사이클:
 * - `main()` 에서 [start] 를 한 번 호출 → 코루틴 스코프가 `sessionStatus` 를 관찰.
 * - Compose 트리는 [route] · [signInState] 를 collect.
 *
 * 계약:
 * - [route] 는 화면 라우팅용 단일 소스. 4 상태 — Splash · Login · Console · SessionExpired.
 * - [signIn] 은 성공 시 화이트리스트 재검증까지 마친 결과를 [SignInResult] 로 돌려준다.
 *   화이트리스트 실패 시 자동으로 signOut 을 태워 세션을 남기지 않는다.
 * - [signOut] 은 supabase-kt 세션 폐기 + localStorage/sessionStorage 청소.
 *
 * @param supabase 테스트 편의를 위한 훅. 프로덕션은 [AdminSupabase] 를 사용.
 * @param sessionManager 스토리지 스위칭에 필요한 매니저. 기본값은 [AdminSupabase.sessionManager].
 * @param scope 상태 관찰 코루틴 스코프. 기본값은 앱 수명 동안 유지되는 SupervisorJob.
 */
class AdminAuthController(
    private val supabase: io.github.jan.supabase.SupabaseClient = AdminSupabase.client,
    private val sessionManager: BrowserStorageSessionManager = AdminSupabase.sessionManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val adminUids: Set<String> = AdminWebConfig.adminUids

    /** 관찰용 원본 세션 상태 (supabase-kt) — StateFlow 형태 유지. */
    val sessionStatus: StateFlow<SessionStatus> = supabase.auth.sessionStatus

    /**
     * 현재 세션의 UID 가 관리자 화이트리스트에 있는지 여부.
     *
     * `sessionStatus` 를 관찰해 파생. UI 는 이 값만 보면 관리자 콘솔 진입 가능 여부를 알 수 있다.
     */
    private val _isAdmin = MutableStateFlow(computeIsAdmin(sessionStatus.value))
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    /**
     * 화면 라우팅용 단일 상태.
     *
     * 초기값은 [AdminRoute.Splash] — supabase-kt 가 저장소에서 세션을 복원 중일 때의 자연스러운
     * 표시. `onAuthStateChange` 가 결과를 내면 즉시 Login/Console 로 갱신.
     *
     * `SIGNED_OUT` 이 refresh 실패로 발생한 경우 [AdminRoute.SessionExpired] 로 표시 →
     * UI 가 스낵바를 띄우고 Login 으로 리라우트.
     */
    private val _route = MutableStateFlow<AdminRoute>(AdminRoute.Splash)
    val route: StateFlow<AdminRoute> = _route.asStateFlow()

    /** UI 에서 로그인 진행 중 스피너/에러를 그리기 위한 부분 상태. */
    private val _signInState = MutableStateFlow<SignInState>(SignInState.Idle)
    val signInState: StateFlow<SignInState> = _signInState.asStateFlow()

    /**
     * 세션 관찰을 시작한다. `main()` 에서 최초 1회만 호출.
     *
     * `sessionStatus` 는 부트 시 저장된 세션이 있으면 `LoadingFromStorage → Authenticated`,
     * 없으면 `LoadingFromStorage → NotAuthenticated` 로 흐른다.
     */
    fun start() {
        sessionStatus
            .onEach { status ->
                _isAdmin.value = computeIsAdmin(status)
                _route.value = when (status) {
                    is SessionStatus.Initializing -> AdminRoute.Splash
                    is SessionStatus.Authenticated -> {
                        val uid = status.session.user?.id
                        if (uid != null && uid in adminUids) {
                            AdminRoute.Console(uid)
                        } else {
                            // 관리자 자격이 없는 상태로 세션이 유효한 경우.
                            // 사용자를 콘솔로 들여보내지 않고 세션을 즉시 폐기 → 로그인 화면 노출.
                            runCatching { signOutInternal() }
                                .onFailure { println("[AdminAuth] 화이트리스트 미통과 signOut 실패: $it") }
                            AdminRoute.Login(reason = LoginReason.NotAdmin)
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        // signOutSource 가 EXTERNAL 이면 서버측에서 세션이 무효화된 상황이지만
                        // 실사용에서는 사용자 의도적 signOut 과 구분이 크지 않으므로 Login 으로.
                        AdminRoute.Login(reason = LoginReason.None)
                    }
                    is SessionStatus.RefreshFailure -> {
                        // 리프레시 토큰 만료/폐기 등. 사용자에게 스낵바 안내.
                        AdminRoute.SessionExpired
                    }
                }
            }
            .launchIn(scope)
    }

    /**
     * 이메일/비밀번호로 로그인 시도. 성공 시 관리자 화이트리스트를 재검증.
     *
     * @param email 관리자 이메일. 공백은 상위 폼에서 검증되어 있다고 가정하지만 여기서도 trim.
     * @param password 비밀번호. 그대로 supabase 로 전달.
     * @param rememberMe `true` 면 저장소를 `localStorage` 로 유지, `false` 면 `sessionStorage` 로 이동.
     */
    suspend fun signIn(email: String, password: String, rememberMe: Boolean): SignInResult {
        _signInState.value = SignInState.InProgress
        return runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            val session: UserSession? = supabase.auth.currentSessionOrNull()
            val uid = session?.user?.id
                ?: return@runCatching SignInResult.Failure(
                    reason = SignInFailure.Unknown,
                    message = "세션이 발급되지 않았어요. 다시 시도해주세요.",
                )
            if (uid !in adminUids) {
                // 세션은 발급됐지만 관리자 자격 없음 → 즉시 signOut 하고 인라인 에러 반환.
                runCatching { signOutInternal() }
                    .onFailure { println("[AdminAuth] 관리자 미허용 signOut 실패: $it") }
                return@runCatching SignInResult.Failure(
                    reason = SignInFailure.NotAdmin,
                    message = "관리자 권한이 없는 계정입니다.",
                )
            }
            // 자동 로그인 체크 상태에 맞춰 저장소를 이동. rememberMe = true 는 기본값 유지.
            val targetStorage = if (rememberMe) BrowserStorageKind.LOCAL else BrowserStorageKind.SESSION
            runCatching { sessionManager.switchTo(targetStorage) }
                .onFailure { println("[AdminAuth] 세션 스토리지 이동 실패: $it") }
            SignInResult.Success(uid = uid)
        }
            .onFailure { println("[AdminAuth] signIn 실패: $it") }
            .getOrElse { throwable ->
                SignInResult.Failure(
                    reason = SignInFailure.InvalidCredentials,
                    message = throwable.message ?: "로그인에 실패했어요. 잠시 후 다시 시도해주세요.",
                )
            }
            .also { result -> _signInState.value = SignInState.Done(result) }
    }

    /**
     * 로그아웃. `supabase.auth.signOut()` 을 태우고 브라우저 저장소를 양쪽 모두 정리.
     */
    suspend fun signOut() {
        signOutInternal()
    }

    /** 세션 상태로부터 관리자 자격 여부를 계산. `Authenticated` 이면서 uid 가 화이트리스트에 있어야 true. */
    private fun computeIsAdmin(status: SessionStatus): Boolean =
        status is SessionStatus.Authenticated && (status.session.user?.id in adminUids)

    private suspend fun signOutInternal() {
        runCatching { supabase.auth.signOut() }
            .onFailure { println("[AdminAuth] supabase signOut 실패 (무시): $it") }
        // supabase 가 세션 매니저의 deleteSession 을 호출하지만, 이중 안전장치로 직접도 클리어.
        runCatching {
            localStorage.removeItem(BrowserStorageSessionManager.STORAGE_KEY)
            sessionStorage.removeItem(BrowserStorageSessionManager.STORAGE_KEY)
        }.onFailure { println("[AdminAuth] 브라우저 저장소 청소 실패 (무시): $it") }
    }
}

/**
 * 콘솔 최상위 라우트. `AdminAuthController.route` 가 이 하나로 화면을 결정한다.
 */
sealed interface AdminRoute {
    /** 세션 복원 중 (스플래시). 부트 직후 매우 짧게 노출. */
    data object Splash : AdminRoute

    /** 로그인 화면. [reason] 이 있으면 카드 하단에 인라인 에러/안내 표시. */
    data class Login(val reason: LoginReason) : AdminRoute

    /** 발송 콘솔. [uid] 는 top bar 프로필 표기용 (선택). */
    data class Console(val uid: String) : AdminRoute

    /** 세션 만료 — refresh 실패. UI 가 스낵바 띄우고 Login 으로 리다이렉트. */
    data object SessionExpired : AdminRoute
}

/** 로그인 화면에 표시할 안내 사유. */
enum class LoginReason {
    /** 특별한 안내 없음. */
    None,

    /** 세션은 유효했지만 관리자 화이트리스트에 없어 자동 로그아웃된 상태. */
    NotAdmin,
}

/**
 * 로그인 결과 — 성공 시 uid, 실패 시 사유 · 사용자 문구를 담는다.
 */
sealed interface SignInResult {
    data class Success(val uid: String) : SignInResult
    data class Failure(val reason: SignInFailure, val message: String) : SignInResult
}

/** 로그인 실패 사유 분류. */
enum class SignInFailure {
    /** 이메일/비밀번호 불일치 등 supabase-kt 예외. */
    InvalidCredentials,

    /** 인증은 성공했지만 관리자 화이트리스트에 없음. */
    NotAdmin,

    /** 예기치 못한 실패. */
    Unknown,
}

/** 로그인 진행 상태 — 스피너 표시용. */
sealed interface SignInState {
    data object Idle : SignInState
    data object InProgress : SignInState
    data class Done(val result: SignInResult) : SignInState
}
