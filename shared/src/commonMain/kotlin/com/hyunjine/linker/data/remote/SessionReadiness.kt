package com.hyunjine.linker.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** 조회 직전 세션 상태 판정 결과 ([awaitUsableSession]). */
enum class SessionReadiness {
    /** 유효한 (만료 전) 세션으로 조회 가능. */
    Ready,

    /** 확실히 로그아웃 — 빈 결과가 진짜 "데이터 없음" 이다. */
    LoggedOut,

    /** 복원 중 · 갱신 재시도 중 · 갱신 실패 — 지금 조회하면 "세션 없음" 이 "데이터 없음" 으로 보인다. */
    Unavailable,
}

/** 세션 확정을 기다리는 기본 최대 시간. silent push 실행 한도 (~30초) 안에 끝나도록. */
val DefaultSessionWaitTimeout: Duration = 15.seconds

/** 동시에 여러 조회가 만료 토큰을 발견해도 refresh 는 한 번만 돌도록 직렬화. */
private val sessionRefreshMutex = Mutex()

/**
 * 토큰이 [now] + [margin] 이후에 만료되면 그대로 써도 되는 세션.
 *
 * @param expiresAt 세션 access token 만료 시각.
 * @param now 현재 시각.
 * @param margin 이 시간 안에 만료될 토큰은 미리 갱신해야 하는 것으로 본다.
 */
@OptIn(ExperimentalTime::class)
fun isSessionFresh(expiresAt: Instant, now: Instant, margin: Duration): Boolean = expiresAt > now + margin

/**
 * 세션이 "로그인됨" 또는 "로그아웃" 으로 확정될 때까지 기다린다 (#367 · #422). 복원 중 (`Initializing`) 이나
 * 갱신 재시도 중 (`RefreshFailure` — 라이브러리가 10초마다 자동 재시도) 이면 그 결과를 기다림.
 * 로그인됨이어도 토큰이 곧 만료되면 먼저 갱신한다 (백그라운드에서 깨어나 자동 갱신 타이머가 못 돈 경우).
 *
 * warm 프로세스 재진입 직후엔 상태가 여전히 `Authenticated` 인데 토큰은 만료돼 있어 그대로 조회하면
 * 401 · 혹은 갱신 실패로 `currentUserOrNull()` 이 null 이 되면서 "빈 결과 성공" 으로 보인다. 조회 전 이 함수로 거른다.
 *
 * @param timeout 세션이 로그인됨 · 로그아웃으로 확정되길 기다리는 최대 시간. 넘기면 [SessionReadiness.Unavailable].
 * @param expiryMargin 이 시간 안에 만료될 토큰은 조회 전에 갱신.
 * @param logTag 로그 prefix (예: "Widget", "Main").
 */
@OptIn(ExperimentalTime::class)
suspend fun awaitUsableSession(
    timeout: Duration = DefaultSessionWaitTimeout,
    expiryMargin: Duration,
    logTag: String,
): SessionReadiness {
    val auth = SupabaseProvider.client.auth
    val status = withTimeoutOrNull(timeout) {
        auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
    }
    return when (status) {
        is SessionStatus.NotAuthenticated -> SessionReadiness.LoggedOut
        is SessionStatus.Authenticated -> {
            if (isSessionFresh(status.session.expiresAt, Clock.System.now(), expiryMargin)) {
                SessionReadiness.Ready
            } else {
                val refreshed = sessionRefreshMutex.withLock {
                    // 락을 기다리는 동안 다른 조회가 이미 갱신했으면 다시 refresh 하지 않는다.
                    val current = auth.currentSessionOrNull()
                    if (current != null && isSessionFresh(current.expiresAt, Clock.System.now(), expiryMargin)) {
                        true
                    } else {
                        runCatching { auth.refreshCurrentSession() }
                            .onFailure { if (it is CancellationException) throw it }
                            .onFailure { println("[$logTag] 토큰 갱신 실패: $it") }
                            .isSuccess
                    }
                }
                if (refreshed) SessionReadiness.Ready else SessionReadiness.Unavailable
            }
        }
        else -> {
            println("[$logTag] 세션 확정 대기 시간 초과")
            SessionReadiness.Unavailable
        }
    }
}
