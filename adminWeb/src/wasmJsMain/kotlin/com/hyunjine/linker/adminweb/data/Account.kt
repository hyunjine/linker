package com.hyunjine.linker.adminweb.data

/**
 * 관리자 콘솔에서 다루는 "푸시 발송 대상 계정" 도메인 모델.
 *
 * [AdminUserRowRaw] (DB row + user_devices embed) 를 접어 만든 뷰. 한 유저가 iOS · Android
 * 기기를 모두 갖고 있어도 하나의 [Account] 로 표현되고, [platforms] 에 두 값이 담긴다.
 * — docs/261-admin-push-console.md §3.3 · §9.
 *
 * @property id `public.users.id` (auth.uid).
 * @property nickname 표시 닉네임. NULL 인 유저는 아직 온보딩 미완료이지만 device 가 있으면 목록에 노출.
 * @property profileImageUrl `users.profile_image_url` — 프로필 이미지 URL. NULL 이면 이니셜 폴백.
 * @property platforms 이 유저가 등록한 기기들의 플랫폼 집합. `user_devices` 가 하나 이상 존재함이
 *                     보장된 상태 (Postgrest `!inner` join) 이므로 비어있지 않다.
 */
data class Account(
    val id: String,
    val nickname: String,
    val profileImageUrl: String?,
    val platforms: Set<Platform>,
)
