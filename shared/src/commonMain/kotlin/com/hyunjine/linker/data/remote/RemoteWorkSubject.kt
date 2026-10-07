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
     * 제목 속 이름과 내 이름이 같은지. 공백 차이 (`양 현진`) 는 무시한다.
     *
     * @param subjectName [Parsed.name].
     * @param myName 설정한 내 이름.
     */
    fun sameName(subjectName: String, myName: String): Boolean =
        subjectName.filterNot { it.isWhitespace() } == myName.filterNot { it.isWhitespace() }

    // 재택(이름) 뒤 첫 시각 범위. 구분자 (` ~ ` · `-` 등) 는 그룹으로 잡아 그대로 되돌려 쓴다.
    private val TimeRange = Regex("""(\)\s*)(\d{1,2}:\d{2})(\s*[-~–]\s*)(\d{1,2}:\d{2})""")

    /**
     * Outlook 원본 제목의 시각만 바꾼 새 제목. 이름 · 구분자 · 띄어쓰기 · 뒤에 붙은 메모 등 나머지는
     * 원본 그대로 둔다 (`재택(양현진) 08:30 ~ 17:30` → `재택(양현진) 09:00 ~ 18:00`).
     *
     * @param subject 원본 제목. 재택 형식이 아니면 그대로 반환.
     * @param startTime 새 시작 `HH:mm`.
     * @param endTime 새 종료 `HH:mm`.
     */
    fun withTimes(subject: String, startTime: String, endTime: String): String {
        if (parse(subject) == null) return subject
        val m = TimeRange.find(subject) ?: return subject
        val replaced = m.groupValues[1] + startTime + m.groupValues[3] + endTime
        return subject.replaceRange(m.range, replaced)
    }

    private fun hhmm(h: String, m: String): String? {
        val hour = h.toInt()
        val minute = m.toInt()
        if (hour !in 0..24 || minute !in 0..59 || (hour == 24 && minute != 0)) return null
        return "${hour.toString().padStart(2, '0')}:${m}"
    }
}
