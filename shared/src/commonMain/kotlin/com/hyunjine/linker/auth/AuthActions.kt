package com.hyunjine.linker.auth

import com.hyunjine.linker.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.providers.Kakao
import kotlinx.coroutines.flow.StateFlow

/**
 * Supabase Auth Kakao provider 로 로그인 흐름을 시작한다. 실제 성공/실패는 딥링크 콜백을 통해
 * [SupabaseProvider.client] 의 세션 상태로 전달되므로 여기서는 브라우저(Chrome Custom Tab /
 * ASWebAuthenticationSession) 를 띄우는 것까지가 책임이다.
 *
 * 취소는 supabase-kt 가 sessionStatus 를 그대로 [SessionStatus.NotAuthenticated] 로 유지하므로
 * 별도 처리 불필요. 실패는 예외로 던져지며 UI 계층에서 catch 해 로그만 남기면 된다.
 *
 * **scope 노트**: Supabase gotrue 의 Kakao provider 가 `account_email profile_image profile_nickname`
 * 을 소스에 하드코딩해 무조건 요청한다 (append 만 되지 override 불가). 클라이언트가 별도 scope 를
 * 명시할 필요 · 여지 없음. Kakao Developers → 카카오 로그인 → 동의항목에서 이 세 개가 모두 등록돼
 * 있어야 KOE205 를 피한다.
 */
suspend fun signInWithKakao() {
    println("[Auth] signInWithKakao: enter")
    SupabaseProvider.client.auth.signInWith(Kakao)
    println("[Auth] signInWithKakao: signInWith returned (browser 세션 시작 요청 완료)")
}

/**
 * Supabase Auth 세션 상태 스트림. [SessionStatus.Authenticated] 로 넘어가면 로그인 완료.
 * 앱 시작 시 저장된 세션 복원 중이면 [SessionStatus.Initializing] / [SessionStatus.NotAuthenticated] 를 거친다.
 */
val sessionStatus: StateFlow<SessionStatus>
    get() = SupabaseProvider.client.auth.sessionStatus
