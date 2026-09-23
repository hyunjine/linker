package com.hyunjine.linker.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// public.user_devices upsert 헬퍼. Push 알림 발송 대상 device 등록.
// - 세션 없으면 no-op (로그인 후 재시도 필요 — 앱 시작 흐름이 로그인 완료 후 호출)
// - (user_id, platform) unique 로 유저·플랫폼당 활성 토큰 1개 강제 (#355)
// - unique(fcm_token) 도 함께 걸려 있어 계정 전환 잔재를 upsert 전에 명시 삭제 필요
object UserDevicesRepository {

    @Serializable
    private data class UpsertPayload(
        @SerialName("user_id") val userId: String,
        @SerialName("fcm_token") val fcmToken: String,
        val platform: String,
    )

    /**
     * 로그인 · FCM 토큰 갱신 시 호출.
     *
     * 순서:
     *  1. 같은 fcm_token 이 다른 user_id 에 걸려있으면 삭제 — 계정 전환 잔재 청소.
     *     unique(fcm_token) 위반을 피하고, 물리 디바이스 소유권을 현재 유저로 이관.
     *  2. (user_id, platform) 기준 upsert — 이 유저의 이 플랫폼 활성 토큰 1개로 정규화.
     *     같은 유저가 재설치 · 시뮬레이터 스왑으로 새 토큰을 받아도 예전 row 는 update 로 덮임.
     *
     * @param token 현재 디바이스의 FCM registration token
     * @param platform "ios" 또는 "android"
     */
    suspend fun upsertMyDevice(token: String, platform: String) {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
        if (uid == null) {
            println("[FCM] 세션 없음 — device upsert 스킵")
            return
        }
        runCatching {
            SupabaseProvider.client.from("user_devices").delete {
                filter {
                    eq("fcm_token", token)
                    neq("user_id", uid)
                }
            }
            SupabaseProvider.client.from("user_devices").upsert(
                UpsertPayload(userId = uid, fcmToken = token, platform = platform),
            ) {
                onConflict = "user_id,platform"
            }
        }.onFailure { println("[FCM] user_devices upsert 실패: $it") }
            .onSuccess { println("[FCM] user_devices upsert 성공 platform=$platform token=${token.take(12)}…") }
    }
}
