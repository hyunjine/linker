package com.hyunjine.linker

import com.hyunjine.linker.data.remote.SessionHealth
import com.hyunjine.linker.data.remote.UsersRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class BootstrapProfileTest {

    private val completed = UsersRepository.Profile(id = "u1", profileCompletedAt = "2026-01-01T00:00:00Z")
    private val incomplete = UsersRepository.Profile(id = "u1")

    @Test
    fun completed_profile_is_completed() {
        assertEquals(BootstrapProfileState.Completed, bootstrapProfileStateOf(Result.success(completed)))
    }

    @Test
    fun incomplete_or_missing_row_is_incomplete() {
        assertEquals(BootstrapProfileState.Incomplete, bootstrapProfileStateOf(Result.success(incomplete)))
        assertEquals(BootstrapProfileState.Incomplete, bootstrapProfileStateOf(Result.success(null)))
    }

    @Test
    fun fetch_failure_is_unknown_not_incomplete() {
        assertEquals(
            BootstrapProfileState.Unknown,
            bootstrapProfileStateOf(Result.failure(IllegalStateException("network"))),
        )
    }

    @Test
    fun profile_setup_only_when_confirmed_incomplete() {
        assertEquals(BootstrapDestination.ProfileSetup, bootstrapDestinationOf(BootstrapProfileState.Incomplete))
        assertEquals(BootstrapDestination.Main, bootstrapDestinationOf(BootstrapProfileState.Completed))
        // 재시도 후에도 확인 못하면 프로필 설정이 아니라 Main
        assertEquals(BootstrapDestination.Main, bootstrapDestinationOf(BootstrapProfileState.Unknown))
    }

    @Test
    fun fallback_goes_to_login_only_when_session_is_gone() {
        // 프로필 확인 실패 원인이 세션 소멸이면 Main 이 아니라 로그인 (#437)
        assertEquals(BootstrapDestination.Login, bootstrapFallbackOf(SessionHealth.Gone))
        // 세션이 살아 있거나 판단 불가 (오프라인) 면 예전처럼 Main (#424)
        assertEquals(BootstrapDestination.Main, bootstrapFallbackOf(SessionHealth.Valid))
        assertEquals(BootstrapDestination.Main, bootstrapFallbackOf(SessionHealth.Unknown))
    }
}
