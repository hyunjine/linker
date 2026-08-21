package com.hyunjine.linker.api.couples

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * `docs/api-design.md` §5 Couples. 초대 코드 · 커플 표시 이름 · 기념일.
 */

@Serializable
data class CoupleResponse(
    val id: String,
    val inviteCode: String,
    val displayName: String? = null,
    val startDate: LocalDate? = null,
    val linkedAt: Instant? = null,
    val members: List<CoupleMemberDto>,
    val createdAt: Instant,
)

@Serializable
data class CoupleMemberDto(
    val userId: String,
    val joinedAt: Instant,
    val role: String = "member",
)

@Serializable
data class JoinCoupleRequest(val inviteCode: String)

@Serializable
data class UpdateCoupleRequest(
    val displayName: String? = null,
    val startDate: LocalDate? = null,
)

// ────────── Anniversaries ──────────

@Serializable
data class AnniversaryResponse(
    val id: String,
    val title: String,
    val date: LocalDate,
    val repeatYearly: Boolean,
    val createdAt: Instant,
)

@Serializable
data class AnniversariesResponse(val items: List<AnniversaryResponse>)

@Serializable
data class CreateAnniversaryRequest(
    val title: String,
    val date: LocalDate,
    val repeatYearly: Boolean = true,
)

@Serializable
data class UpdateAnniversaryRequest(
    val title: String? = null,
    val date: LocalDate? = null,
    val repeatYearly: Boolean? = null,
)
