package com.hyunjine.linker.adminweb.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `users` 에 `user_devices!inner(platform)` 를 embed 한 Postgrest 응답 row 의 그대로 형태.
 * — docs/261-admin-push-console.md §4.1.
 *
 * `!inner` 지정 덕분에 device 가 하나 이상 존재하는 유저만 반환되므로 [userDevices] 는 항상
 * 비어있지 않다 (푸시 가능 대상 = FCM 토큰을 가진 유저).
 *
 * @property id `public.users.id`.
 * @property nickname 닉네임. 온보딩 미완료면 서버에서 NULL 이지만, 이 스코프에서는
 *                    non-null 로 취급 (도메인 [Account.nickname] 도 non-null). NULL row 는
 *                    상위 리포지토리 매핑에서 빈 문자열로 대체.
 * @property profileImageUrl 프로필 이미지 URL. `users.profile_image_url` 컬럼. NULL 이면
 *                           UI 는 이니셜로 폴백.
 * @property userDevices 이 유저의 기기 platform 리스트. embed 로 채워진다.
 */
@Serializable
data class AdminUserRowRaw(
    val id: String,
    val nickname: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    @SerialName("user_devices") val userDevices: List<Device> = emptyList(),
) {
    /**
     * `user_devices` embed 안의 최소 필드. 다른 컬럼 (fcm_token, created_at 등) 은
     * 브라우저 측에서 필요치 않아 select 하지 않는다.
     *
     * @property platform `user_devices.platform` (`'ios' | 'android'`).
     */
    @Serializable
    data class Device(val platform: String)
}
