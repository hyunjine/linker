package com.hyunjine.linker.feature.more

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoreVersionTest {

    @Test
    fun compares_numerically_not_lexically() {
        assertTrue(compareVersions("1.5.10", "1.5.9") > 0)
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(compareVersions("1.5.1", "1.5.2") < 0)
        assertEquals(0, compareVersions("1.5", "1.5.0"))
    }

    @Test
    fun update_available_only_when_latest_is_newer() {
        assertTrue(MoreUiState("1.5.1", LatestVersion.Loaded("1.5.2")).updateAvailable)
        assertFalse(MoreUiState("1.5.1", LatestVersion.Loaded("1.5.1")).updateAvailable)
        // 로컬 빌드가 릴리즈보다 앞서도 "업데이트" 로 보지 않음
        assertFalse(MoreUiState("1.6.0", LatestVersion.Loaded("1.5.2")).updateAvailable)
        assertFalse(MoreUiState("1.5.1", LatestVersion.Loading).updateAvailable)
        assertFalse(MoreUiState("1.5.1", LatestVersion.Unavailable).updateAvailable)
    }
}
