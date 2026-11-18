package com.hyunjine.linker.feature.main

import com.hyunjine.linker.data.remote.isSessionFresh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class MainRefreshPolicyTest {

    private val oct = YearMonth(2026, 10)
    private val nov = YearMonth(2026, 11)
    private val sep = YearMonth(2026, 9)

    @Test
    fun missing_months_are_always_refetched_even_when_throttled() {
        assertEquals(
            setOf(nov),
            monthsToRefetch(requested = setOf(oct, nov), cached = setOf(oct), includeCached = false),
        )
    }

    @Test
    fun throttle_passed_refetches_cached_and_missing_months() {
        assertEquals(
            setOf(sep, oct, nov),
            monthsToRefetch(requested = setOf(oct, nov), cached = setOf(sep, oct), includeCached = true),
        )
    }

    @Test
    fun nothing_to_refetch_when_all_cached_and_throttled() {
        assertTrue(monthsToRefetch(requested = setOf(oct), cached = setOf(oct), includeCached = false).isEmpty())
    }

    @Test
    fun retry_delays_are_bounded_exponential_backoff() {
        assertEquals(listOf(1.seconds, 2.seconds, 4.seconds, 8.seconds), MonthFetchRetryDelays)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun session_fresh_only_when_expiry_beyond_margin() {
        val now = Instant.fromEpochSeconds(1_000_000)
        assertTrue(isSessionFresh(now + 31.seconds, now, 30.seconds))
        assertFalse(isSessionFresh(now + 30.seconds, now, 30.seconds))
        assertFalse(isSessionFresh(now - 5.seconds, now, 30.seconds))
    }
}
