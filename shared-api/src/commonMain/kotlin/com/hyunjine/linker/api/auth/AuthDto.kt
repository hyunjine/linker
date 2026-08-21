package com.hyunjine.linker.api.auth

import com.hyunjine.linker.api.couples.CoupleResponse
import com.hyunjine.linker.api.users.UserResponse
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * `docs/api-design.md` §3 Auth. 서버·클라가 공유.
 */

@Serializable
data class KakaoLoginRequest(val kakaoAccessToken: String)

@Serializable
data class LoginResponse(
    val session: SessionDto,
    val user: UserResponse,
    /** 커플 미연결이면 null. */
    val couple: CoupleResponse? = null,
)

@Serializable
data class SessionDto(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
)

@Serializable
data class RefreshRequest(val refreshToken: String)
