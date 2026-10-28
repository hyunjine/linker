package com.hyunjine.linker

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
}
