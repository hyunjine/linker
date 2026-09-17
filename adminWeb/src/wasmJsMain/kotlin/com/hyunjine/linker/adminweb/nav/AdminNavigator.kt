package com.hyunjine.linker.adminweb.nav

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.hyunjine.linker.adminweb.auth.AdminRoute

/**
 * 관리자 콘솔의 초경량 라우터.
 *
 * 화면이 두 개 (로그인 · 콘솔) 뿐이라 Voyager 등 별도 라이브러리를 도입하기보다는
 * sealed 라우트 + 단일 [State] 로 처리한다 (`docs/261-admin-push-console.md` §5 — "Voyager 또는
 * 자체 route 관리" 옵션 중 후자 선택).
 *
 * 라우팅의 실제 결정은 `AdminAuthController.route` 가 세션 상태로부터 방출하며, 이 네비게이터는
 * 그 값을 관찰해 UI 트리에 노출하는 얇은 어댑터 역할을 한다. UI 가 로그인 성공 등 임의 시점에
 * 화면을 강제로 옮겨야 할 때는 [goToConsole] · [goToLogin] 을 호출해 오버라이드한다.
 *
 * 오버라이드는 다음 세션 상태 변화가 도착하면 자연스럽게 정합화된다 — 컨트롤러가 항상 최종
 * source of truth 다.
 */
class AdminNavigator {

    private val _current = mutableStateOf<AdminRoute>(AdminRoute.Splash)

    /** 현재 라우트. Compose 트리는 이 값을 관찰해 화면을 스왑한다. */
    val current: State<AdminRoute> = _current

    /**
     * 컨트롤러가 관찰 결과를 밀어넣는 진입점.
     *
     * @param route 세션 상태로부터 유도된 최신 라우트.
     */
    fun sync(route: AdminRoute) {
        _current.value = route
    }

    /**
     * 로그인 성공 직후처럼 세션 이벤트를 기다리지 않고 콘솔로 진입하고 싶을 때 호출.
     *
     * @param uid 콘솔이 표시할 로그인 사용자 uid.
     */
    fun goToConsole(uid: String) {
        _current.value = AdminRoute.Console(uid)
    }

    /**
     * 로그아웃 액션 직후처럼 세션 이벤트를 기다리지 않고 로그인 화면으로 이동하고 싶을 때 호출.
     */
    fun goToLogin() {
        _current.value = AdminRoute.Login(reason = com.hyunjine.linker.adminweb.auth.LoginReason.None)
    }
}
