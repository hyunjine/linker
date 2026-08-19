package com.hyunjine.linker.data.specialday

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 특일정보 API 를 (year, kind) 조합으로 캐시하는 in-memory 저장소. 같은 조합 재요청 시 API 미호출.
 * 앱 프로세스 라이프사이클 동안만 유효 (재실행 시 재요청). 디스크 캐시는 후속 이슈.
 */
class SpecialDayRepository(
    private val api: SpecialDayApi = SpecialDayApi(),
) {
    private val cache = mutableMapOf<Key, List<SpecialDayDto>>()
    private val mutex = Mutex()

    /** 실패 시 빈 리스트를 반환하고 예외를 삼킨다 — 캘린더는 특일 없이도 동작해야 함. */
    suspend fun getYear(year: Int, kind: SpecialDayKind): List<SpecialDayDto> = mutex.withLock {
        val key = Key(year, kind)
        cache[key]?.let { return@withLock it }
        val fetched = runCatching { api.fetchYear(year, kind) }
            .onFailure { println("[SpecialDayRepository] $year/$kind fetch 실패: $it") }
            .getOrElse { emptyList() }
        println("[SpecialDayRepository] $year/$kind 결과: ${fetched.size} 개")
        cache[key] = fetched
        fetched
    }

    private data class Key(val year: Int, val kind: SpecialDayKind)
}
