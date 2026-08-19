package com.hyunjine.linker.data.holiday

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 특일정보 API 를 연 단위로 감싸는 in-memory 캐시. 같은 연을 두 번 요청해도 API 는 한 번만 친다.
 * 앱 프로세스 라이프사이클 동안만 유효 (재실행 시 재요청).
 */
class HolidayRepository(
    private val api: HolidayApi = HolidayApi(),
) {
    private val cache = mutableMapOf<Int, List<HolidayDto>>()
    private val mutex = Mutex()

    /** 실패 시 빈 리스트를 반환하고 예외를 삼킨다 — 캘린더는 공휴일 없이도 동작해야 함. */
    suspend fun getYear(year: Int): List<HolidayDto> = mutex.withLock {
        cache[year]?.let { return@withLock it }
        val fetched = runCatching { api.fetchYear(year) }.getOrElse { emptyList() }
        cache[year] = fetched
        fetched
    }
}
