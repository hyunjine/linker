package com.hyunjine.linker.api.schedules

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * `docs/api-design.md` §6 Schedules. 클라 `ScheduleDraft`/`RepeatRule` sealed 와
 * 1:1 매핑되도록 설계.
 */

enum class ScheduleType { task, schedule }
enum class ScheduleOwnerKind { me, partner, us }

/** 반복 규칙. `kind` 별로 사용되는 필드가 다름. `null` 이면 반복 없음. */
enum class RepeatKind { daily, weekly, monthly, yearly, custom }

@Serializable
data class RepeatRuleDto(
    val kind: RepeatKind,
    /** weekly 전용. 0=일 … 6=토. */
    val weeklyDays: List<Int>? = null,
    /** monthly 전용. 1..31. */
    val monthlyDay: Int? = null,
    /** yearly 전용. 1..12. */
    val yearlyMonth: Int? = null,
    /** yearly 전용. 1..31. */
    val yearlyDay: Int? = null,
    /** custom 전용. RFC 5545 rrule 문자열. */
    val customRule: String? = null,
    val endsAt: LocalDate? = null,
    val maxCount: Int? = null,
)

@Serializable
data class CreateScheduleRequest(
    val type: ScheduleType,
    val ownerKind: ScheduleOwnerKind,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val allDay: Boolean = false,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val repeat: RepeatRuleDto? = null,
)

@Serializable
data class UpdateScheduleRequest(
    val ownerKind: ScheduleOwnerKind? = null,
    val title: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val allDay: Boolean? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val repeat: RepeatRuleDto? = null,
)

@Serializable
data class ToggleDoneRequest(val isDone: Boolean)

/** 반복 확장 후 발생일 단위로 내려오는 응답. `series_id == id` 면 비반복 원본. */
@Serializable
data class ScheduleOccurrence(
    val id: String,
    val seriesId: String,
    val occurrenceDate: LocalDate,
    val type: ScheduleType,
    val ownerKind: ScheduleOwnerKind,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val allDay: Boolean,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val isDone: Boolean = false,
    val repeat: RepeatRuleDto? = null,
    val createdBy: String,
    val isEditable: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class ScheduleRange(val from: LocalDate, val to: LocalDate)

@Serializable
data class ScheduleListResponse(
    val range: ScheduleRange,
    val items: List<ScheduleOccurrence>,
)
