package com.hyunjine.linker.feature.dday

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * 디데이 milestone 한 건 (#329). Figma 4176:79095 리스트 row 에 그대로 매핑.
 *
 * @property label "100일" · "1주년" · "500일" 등 사용자에게 보여줄 이름.
 * @property date 실제 도래 날짜.
 * @property deltaDays `date - today` 부호 있는 차이. 미래는 양수 (D-N), 과거는 음수 (D+N 로 표기).
 */
data class DdayMilestone(
    val label: String,
    val date: LocalDate,
    val deltaDays: Int,
) {
    /** D-badge 라벨. 오늘이면 "D-DAY", 미래면 "D-N", 과거면 "D+N". */
    val badgeLabel: String
        get() = when {
            deltaDays == 0 -> "D-DAY"
            deltaDays > 0 -> "D-$deltaDays"
            else -> "D+${-deltaDays}"
        }

    val isPast: Boolean get() = deltaDays < 0
    val isFuture: Boolean get() = deltaDays > 0
}

/**
 * [anchor] 를 시작점으로 milestone 목록 생성.
 *
 * 규칙 (당일 = 1일째 카운팅, 사용자 스펙):
 *  - N일 milestone → anchor 로부터 (N - 1) 일 뒤 (anchor 당일이 1일째이므로).
 *    예) anchor=2025.03.22, 100일 → 2025.06.29 (anchor + 99일)
 *  - 주년 → anchor 로부터 N 년 뒤 (달력 기반 · leap year 도 정확).
 *
 * 반환 리스트는 date 오름차순. 필요 시 caller 가 today 기준으로 upcoming/past 분리.
 */
fun buildMilestones(
    anchor: LocalDate,
    maxDayMilestones: List<Int> = DefaultDayMilestones,
    maxYears: Int = DefaultMaxYears,
): List<Pair<String, LocalDate>> {
    val out = mutableListOf<Pair<String, LocalDate>>()
    maxDayMilestones.forEach { d ->
        // "N일" 은 당일 포함 카운트라 anchor + (N-1) 일. 1일 → anchor 그 날.
        out.add("${d}일" to anchor.plus((d - 1).toLong(), DateTimeUnit.DAY))
    }
    for (y in 1..maxYears) {
        out.add("${y}주년" to anchor.plus(y.toLong(), DateTimeUnit.YEAR))
    }
    return out.sortedBy { it.second }
}

/** [buildMilestones] 결과에 today 기준 delta 를 붙여 [DdayMilestone] 리스트로 변환. */
fun computeMilestones(anchor: LocalDate, today: LocalDate): List<DdayMilestone> =
    buildMilestones(anchor).map { (label, date) ->
        DdayMilestone(label = label, date = date, deltaDays = today.daysUntil(date))
    }

/** 오늘 기준으로 앞으로 도래할 milestone (deltaDays ≥ 0) 만 · 가까운 순. */
fun upcomingMilestones(all: List<DdayMilestone>): List<DdayMilestone> =
    all.filter { it.deltaDays >= 0 }.sortedBy { it.deltaDays }

/** 오늘 기준으로 이미 지난 milestone (deltaDays < 0) 만 · 최근 순 (음수 절대값 작은 것부터). */
fun pastMilestones(all: List<DdayMilestone>): List<DdayMilestone> =
    all.filter { it.deltaDays < 0 }.sortedByDescending { it.deltaDays }

/**
 * anchor 부터 today 까지 며칠째 (당일 = 1일째, 사용자 스펙). D+N 카운터에 그대로 노출.
 * anchor.daysUntil 은 두 날짜 간 "완료된 하루 수" 라 anchor 당일은 0 → +1 로 보정.
 * anchor 가 미래인 케이스는 UI (picker maxDate = today) 에서 이미 차단되어 여기선 고려 X.
 */
fun daysSinceAnchor(anchor: LocalDate, today: LocalDate): Int =
    anchor.daysUntil(today) + 1

/** 기본 일수 milestone 프리셋. 50일 · 100단위 · 500/1000/2000 (일반적으로 앱들이 채택하는 패턴). */
private val DefaultDayMilestones: List<Int> =
    listOf(50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 2000, 3000, 5000, 10000)

/** 기본 주년 상한. 앱 실제 사용 범위 커버. */
private const val DefaultMaxYears: Int = 30
