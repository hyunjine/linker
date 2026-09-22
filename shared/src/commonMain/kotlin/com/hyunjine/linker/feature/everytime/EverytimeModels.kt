package com.hyunjine.linker.feature.everytime

/**
 * 에브리타임 시간표 조회 응답을 앱 도메인 모델로 정규화한 결과 (#306).
 *
 * 시간 축 표현 규약:
 *  - `startSlot` / `endSlot` 은 하루를 5 분 단위로 나눈 슬롯 인덱스 (`0..287`).
 *    예) `108` → 09:00, `180` → 15:00. 시 = slot/12, 분 = (slot%12) * 5.
 *  - `day` 는 `0=월` .. `6=일`. Everytime 응답이 이미 이 값을 그대로 준다.
 */
data class EverytimeTimetable(
    val ownerName: String,
    val year: Int?,
    val semester: Int?,
    val identifier: String,
    val lectures: List<Lecture>,
    val availableSemesters: List<SemesterRef>,
)

data class Lecture(
    val id: String,
    val name: String,
    val professor: String,
    val credit: Int,
    val defaultPlace: String,
    val slots: List<TimeSlot>,
)

data class TimeSlot(
    val day: Int,
    val startSlot: Int,
    val endSlot: Int,
    val place: String,
) {
    /** 시(24h) 를 반환 — `108` → 9. */
    fun startHour(): Int = startSlot / 12
    fun startMinuteInHour(): Int = (startSlot % 12) * 5
    fun endHour(): Int = endSlot / 12
    fun endMinuteInHour(): Int = (endSlot % 12) * 5
}

/** `primaryTables` 아래의 학기 목록. 학기 스위처가 이 목록을 그대로 노출. */
data class SemesterRef(
    val year: Int,
    val semester: Int,
    val identifier: String,
)
