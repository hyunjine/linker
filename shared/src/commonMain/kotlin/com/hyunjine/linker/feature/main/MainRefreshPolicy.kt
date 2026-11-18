package com.hyunjine.linker.feature.main

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 월 일정 조회 재시도 사이 대기 시간 (#422). 1초 → 2초 → 4초 → 8초 지수 backoff — 첫 시도 포함 최대 5회.
 * 백그라운드 복귀 직후 끊긴 연결 · 토큰 갱신이 수 초 걸리는 경우까지 커버하되 무한 재시도는 하지 않는다.
 */
val MonthFetchRetryDelays: List<Duration> = listOf(1.seconds, 2.seconds, 4.seconds, 8.seconds)

/**
 * 재진입 · 세션 복구 시 다시 받을 달 (#422).
 *
 * 캐시에 없는 달 (조회 실패로 비어 있는 달 포함) 은 **항상** 포함하고, 이미 캐시된 달은 [includeCached] 일 때만
 * 포함한다 (30초 throttle 은 캐시된 달 재조회에만 적용).
 *
 * @param requested 화면에 한 번이라도 요청된 달.
 * @param cached 지금 캐시에 들어 있는 달.
 * @param includeCached 캐시된 달까지 다시 받을지 (throttle 통과 여부).
 */
fun monthsToRefetch(
    requested: Set<YearMonth>,
    cached: Set<YearMonth>,
    includeCached: Boolean,
): Set<YearMonth> = if (includeCached) requested + cached else requested - cached
