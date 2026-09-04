package com.hyunjine.linker.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.auth.AppleLoginClient
import com.hyunjine.linker.auth.KakaoLoginClient
import com.hyunjine.linker.auth.signInWithApple
import com.hyunjine.linker.auth.signInWithEmail
import com.hyunjine.linker.auth.signInWithKakao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** LoginScreen — 카카오 버튼 탭 트리거만 담당. 세션 상태는 앱 최상위 sessionStatus flow 가 반영. */
class LoginViewModel : ViewModel() {

    /** DEBUG 전용 email 로그인 상태. UI 는 [debugError] 로 실패 사유 표시. */
    private val _debugError = MutableStateFlow<String?>(null)
    val debugError: StateFlow<String?> = _debugError.asStateFlow()

    fun onKakaoLoginClick(client: KakaoLoginClient) {
        println("[Auth] 카카오 버튼 click")
        viewModelScope.launch {
            runCatching { signInWithKakao(client) }
                .onFailure { println("[Auth] signInWithKakao 실패: $it") }
        }
    }

    fun onAppleLoginClick(client: AppleLoginClient) {
        println("[Auth] Apple 버튼 click")
        viewModelScope.launch {
            runCatching { signInWithApple(client) }
                .onFailure { println("[Auth] signInWithApple 실패: $it") }
        }
    }

    /** DEBUG 전용 — 시트에서 입력받은 email/password 로 Supabase 로그인. */
    fun onDebugEmailLogin(email: String, password: String, onDone: () -> Unit) {
        _debugError.value = null
        viewModelScope.launch {
            runCatching { signInWithEmail(email.trim(), password) }
                .onSuccess { onDone() }
                .onFailure {
                    println("[Auth] signInWithEmail 실패: $it")
                    _debugError.value = it.message ?: "로그인 실패"
                }
        }
    }

    fun clearDebugError() { _debugError.value = null }
}
