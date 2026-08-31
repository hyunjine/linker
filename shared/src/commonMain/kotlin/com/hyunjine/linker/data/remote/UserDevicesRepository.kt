package com.hyunjine.linker.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// public.user_devices upsert 헬퍼. Push 알림 발송 대상 device 등록.
// - 세션 없으면 no-op (로그인 후 재시도 필요 — 앱 시작 흐름이 로그인 완료 후 호출)
// - (user_id, fcm_token) unique 로 중복 방지, on-conflict update 로 timestamp 만 갱신
object UserDevicesRepository {

    @Serializable
    private data class UpsertPayload(
        @SerialName("user_id") val userId: String,
        @SerialName("fcm_token") val fcmToken: String,
        val platform: String,
    )

    suspend fun upsertMyDevice(token: String, platform: String) {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
        if (uid == null) {
            println("[FCM] 세션 없음 — device upsert 스킵")
            return
        }
        runCatching {
            SupabaseProvider.client.from("user_devices").upsert(
                UpsertPayload(userId = uid, fcmToken = token, platform = platform),
            ) {
                onConflict = "user_id,fcm_token"
            }
        }.onFailure { println("[FCM] user_devices upsert 실패: $it") }
            .onSuccess { println("[FCM] user_devices upsert 성공 platform=$platform token=${token.take(12)}…") }
    }
}
