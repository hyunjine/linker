package com.hyunjine.linker.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SessionHealthTest {

    @Test
    fun refresh_token_rejection_is_gone() {
        // refresh_token_not_found · 폐기 · 유저 삭제 (400), 토큰 · 세션 거부 (401 · 403)
        listOf(400, 401, 403, 404, 422).forEach {
            assertTrue(isSessionRejection(it), "status $it")
            assertEquals(SessionHealth.Gone, sessionHealthOfRefreshFailure(it))
        }
    }

    @Test
    fun network_and_server_errors_are_unknown_not_logout() {
        // 응답 없음 (오프라인) · 5xx · 타임아웃 · rate limit 은 로그아웃하지 않는다
        listOf(null, 408, 429, 500, 502, 503, 504, 520).forEach {
            assertFalse(isSessionRejection(it), "status $it")
            assertEquals(SessionHealth.Unknown, sessionHealthOfRefreshFailure(it))
        }
    }

    @Test
    fun resume_check_skipped_before_bootstrap() {
        assertFalse(shouldCheckSessionOnResume(bootstrapped = false, sinceLastCheck = null, minInterval = 30.seconds))
        assertFalse(shouldCheckSessionOnResume(bootstrapped = false, sinceLastCheck = 60.seconds, minInterval = 30.seconds))
    }

    @Test
    fun resume_check_runs_first_time_and_after_interval() {
        assertTrue(shouldCheckSessionOnResume(bootstrapped = true, sinceLastCheck = null, minInterval = 30.seconds))
        assertTrue(shouldCheckSessionOnResume(bootstrapped = true, sinceLastCheck = 30.seconds, minInterval = 30.seconds))
    }

    @Test
    fun resume_check_debounced_within_interval() {
        assertFalse(shouldCheckSessionOnResume(bootstrapped = true, sinceLastCheck = 5.seconds, minInterval = 30.seconds))
    }
}
