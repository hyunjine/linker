package com.hyunjine.linker.data.remote

/**
 * Outlook 재택 일정 제목 규칙 (#242). 회사 캘린더에 `재택(이름) HH:MM-HH:MM` 형식으로 올라오는
 * 일정만 Linker 로 미러링하고, 미러 쪽 제목은 `재택`, 시각은 제목 안의 시각으로 정규화한다.
 *
 * 앱에서 미러를 수정하면 원본 Outlook 제목도 같은 형식으로 다시 써야 다음 동기화 때 계속 매칭되므로
 * 파싱 · 생성을 한곳에 둔다.
 */
object RemoteWorkSubject {

    /** 미러 일정 제목. */
    const val MirrorTitle = "재택"

    // 예: "재택(현진) 9:00-18:00", "재택 (현진) 09:30 ~ 18:30". 구분자는 - · ~ · – 허용.
    private val Pattern = Regex("""^\s*재택\s*\(([^)]*)\)\s*(\d{1,2}):(\d{2})\s*[-~–]\s*(\d{1,2}):(\d{2})""")

    /**
     * @param name 괄호 안 이름 (`현진`).
     * @param startTime `HH:mm`.
     * @param endTime `HH:mm`.
     */
    data class Parsed(val name: String, val startTime: String, val endTime: String)

    /**
     * 재택 형식이면 이름 · 시각을 돌려준다. 형식이 아니거나 시각이 범위를 벗어나면 null.
     *
     * @param subject Outlook 일정 제목.
     */
    fun parse(subject: String?): Parsed? {
        val m = Pattern.find(subject ?: return null) ?: return null
        val (name, sh, sm, eh, em) = m.destructured
        val start = hhmm(sh, sm) ?: return null
        val end = hhmm(eh, em) ?: return null
        return Parsed(name = name.trim(), startTime = start, endTime = end)
    }

    /**
     * Outlook 원본에 쓸 제목. 원본의 이름은 유지하고 시각만 바꾼다.
     *
     * @param name 괄호 안 이름.
     * @param startTime `HH:mm`.
     * @param endTime `HH:mm`.
     */
    fun format(name: String, startTime: String, endTime: String): String = "재택($name) $startTime-$endTime"

    private fun hhmm(h: String, m: String): String? {
        val hour = h.toInt()
        val minute = m.toInt()
        if (hour !in 0..24 || minute !in 0..59 || (hour == 24 && minute != 0)) return null
        return "${hour.toString().padStart(2, '0')}:${m}"
    }
}
