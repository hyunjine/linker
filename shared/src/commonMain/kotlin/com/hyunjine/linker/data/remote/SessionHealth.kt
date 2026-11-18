package com.hyunjine.linker.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * 세션 검사 ([checkSessionHealth]) 결과 (#437).
 * [SessionReadiness] 와 달리 "갱신 실패" 를 일시 오류와 세션 소멸로 나눈다 — 로그아웃 여부를 정해야 하므로.
 */
enum class SessionHealth {
    /** 쓸 수 있는 세션 — 서버가 토큰을 받아줬거나 방금 갱신에 성공. */
    Valid,

    /** 세션이 확실히 사라짐 — 로그아웃 상태 · refresh token 거부 (만료 · 폐기 · 유저 삭제). 로그인으로 보내야 한다. */
    Gone,

    /** 네트워크 · 서버 오류 · 복원 대기 시간 초과로 지금은 판단 불가. 로그아웃하지 않고 보던 화면 유지 (#424). */
    Unknown,
}

/** 일시 오류로 보는 4xx — 요청 타임아웃 · rate limit. 세션 자체의 문제가 아니다. */
private val TRANSIENT_CLIENT_ERROR_CODES = setOf(408, 429)

/**
 * Auth 서버 응답 코드가 "이 세션은 더 이상 유효하지 않다" 는 확정 응답인지.
 * refresh token 거부는 400 (`refresh_token_not_found` 등), 토큰 · 세션 거부는 401 · 403 으로 온다.
 * 5xx · 네트워크 오류 (코드 없음) · 408 · 429 는 일시 오류라 false — supabase-kt 도 5xx · 네트워크 오류는
 * 재시도하고 그 외 응답에서만 세션을 지운다 (`AuthImpl.tryImportingSession`).
 *
 * @param statusCode HTTP 상태 코드. 응답을 못 받은 (네트워크) 오류면 null.
 */
fun isSessionRejection(statusCode: Int?): Boolean =
    statusCode != null && statusCode in 400..499 && statusCode !in TRANSIENT_CLIENT_ERROR_CODES

/**
 * refresh 요청 실패 → 세션 상태. 확정 거부만 [SessionHealth.Gone], 나머지는 [SessionHealth.Unknown].
 *
 * @param statusCode 실패한 refresh 요청의 HTTP 상태 코드. 네트워크 오류면 null.
 */
fun sessionHealthOfRefreshFailure(statusCode: Int?): SessionHealth =
    if (isSessionRejection(statusCode)) SessionHealth.Gone else SessionHealth.Unknown

/**
 * 포그라운드 복귀 시 세션 검사를 돌릴지. 부트스트랩 (첫 화면 결정) 전이면 부트스트랩이 세션을 다루므로
 * 건너뛰고, 직전 검사 후 [minInterval] 안이면 (콜드 스타트 직후 · 연속 resume) 중복 검사를 건너뛴다.
 *
 * @param bootstrapped 이번 로그인에서 부트스트랩 라우팅을 마쳤는지.
 * @param sinceLastCheck 직전 검사 후 지난 시간. 검사한 적 없으면 null.
 * @param minInterval 이 간격 안의 재검사는 생략.
 */
fun shouldCheckSessionOnResume(bootstrapped: Boolean, sinceLastCheck: Duration?, minInterval: Duration): Boolean =
    bootstrapped && (sinceLastCheck == null || sinceLastCheck >= minInterval)

/**
 * 세션이 실제로 쓸 수 있는지 서버까지 확인한다 (#437). 포그라운드 복귀 · 부트스트랩 폴백에서 사용.
 *
 * - 복원 중 (`Initializing` — Android 는 백그라운드 진입 시 이 상태로 돌렸다가 복귀 시 다시 로드) 이면 끝날 때까지 대기.
 * - `NotAuthenticated` → [SessionHealth.Gone]. `RefreshFailure` → [SessionHealth.Unknown] (supabase-kt 가 재시도 중이고,
 *   거부 응답을 받으면 스스로 세션을 지운다).
 * - 토큰이 만료 전이면 `GET /user` 로 서버가 받아주는지 확인. 거부 (401 · 403 등) 면 시계 오차일 수도 있으니
 *   바로 로그아웃하지 않고 refresh 로 최종 판정.
 * - 토큰이 만료 · 만료 임박이거나 위에서 거부되면 refresh. refresh token 이 거부되면 [SessionHealth.Gone].
 *
 * iOS 는 supabase-kt 의 lifecycle 처리가 없어 (Android 전용) 백그라운드에서 돌아오면 상태는 `Authenticated` 인데
 * 토큰은 만료된 채일 수 있고, 수동 갱신 (`refreshCurrentSession`) 실패는 세션을 지우지 않아 `NotAuthenticated` 로
 * 넘어가지 않는다. 이 함수는 판정만 하고 세션을 지우지 않는다 — [SessionHealth.Gone] 처리는 호출부 몫.
 *
 * @param timeout 전체 검사 최대 시간. 넘기면 [SessionHealth.Unknown].
 * @param expiryMargin 이 시간 안에 만료될 토큰은 서버 확인 대신 바로 갱신한다.
 * @param logTag 로그 prefix (예: "Resume", "Boot").
 */
@OptIn(ExperimentalTime::class)
suspend fun checkSessionHealth(
    timeout: Duration = DefaultSessionWaitTimeout,
    expiryMargin: Duration,
    logTag: String,
): SessionHealth = withTimeoutOrNull(timeout) { checkSessionHealthNow(expiryMargin, logTag) }
    ?: SessionHealth.Unknown.also { println("[$logTag] 세션 검사 시간 초과 — 판단 보류") }

/**
 * [checkSessionHealth] 본체 (타임아웃 없음).
 *
 * @param expiryMargin 이 시간 안에 만료될 토큰은 서버 확인 대신 바로 갱신한다.
 * @param logTag 로그 prefix.
 */
@OptIn(ExperimentalTime::class)
private suspend fun checkSessionHealthNow(expiryMargin: Duration, logTag: String): SessionHealth {
    val auth = SupabaseProvider.client.auth
    val status = auth.sessionStatus.first { it !is SessionStatus.Initializing }
    val session = when (status) {
        is SessionStatus.NotAuthenticated -> return SessionHealth.Gone
        is SessionStatus.RefreshFailure -> return SessionHealth.Unknown
        is SessionStatus.Initializing -> return SessionHealth.Unknown
        is SessionStatus.Authenticated -> status.session
    }

    if (isSessionFresh(session.expiresAt, Clock.System.now(), expiryMargin)) {
        val rejectedCode = try {
            auth.retrieveUser(session.accessToken)
            return SessionHealth.Valid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? RestException)?.statusCode
            if (!isSessionRejection(code)) {
                println("[$logTag] 세션 확인 실패 (일시 오류로 보고 유지): $e")
                return SessionHealth.Unknown
            }
            code
        }
        println("[$logTag] 서버가 토큰을 거부 ($rejectedCode) — refresh 로 최종 판정")
    } else {
        println("[$logTag] 토큰 만료 · 만료 임박 — refresh")
    }

    return sessionRefreshMutex.withLock {
        // 락을 기다리는 동안 다른 곳에서 이미 갱신했으면 (토큰이 바뀌고 만료 전) 그대로 사용.
        val current = auth.currentSessionOrNull()
        when {
            current == null ->
                if (auth.sessionStatus.value is SessionStatus.NotAuthenticated) SessionHealth.Gone else SessionHealth.Unknown
            current.accessToken != session.accessToken &&
                isSessionFresh(current.expiresAt, Clock.System.now(), expiryMargin) -> SessionHealth.Valid
            else -> try {
                auth.refreshCurrentSession()
                SessionHealth.Valid
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionHealthOfRefreshFailure((e as? RestException)?.statusCode)
                    .also { println("[$logTag] 토큰 갱신 실패 → $it: $e") }
            }
        }
    }
}
