package com.hyunjine.linker.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 커플 관련 서버 상호작용. RLS 로는 초대코드 lookup · 원자적 join 이 불가능해
 * `public.create_my_couple()` · `public.join_couple_by_invite(text)` 두 RPC 로 위임.
 */
object CouplesRepository {

    /**
     * 서버 RPC 응답 형태. `create_my_couple` 은 `TABLE(id UUID, invite_code TEXT)` 반환.
     */
    @Serializable
    data class MyCouple(
        val id: String,
        @SerialName("invite_code") val inviteCode: String,
    )

    /**
     * 현재 유저의 커플을 조회한다. 없으면 새 커플을 만들고 자신을 첫 멤버로 join.
     * 재진입 시 같은 커플이 반환되어 idempotent.
     */
    suspend fun createOrGetMyCouple(): MyCouple {
        val result = SupabaseProvider.client.postgrest.rpc("create_my_couple")
        return result.decodeList<MyCouple>().first()
    }

    /**
     * 초대코드로 상대방 커플에 합류한다. 기존 소속 커플에서 자동 leave.
     * 서버가 코드 정규화 (upper + trim) 하므로 클라이언트는 raw 값 그대로 전달해도 됨.
     *
     * @return 합류한 couple_id
     * @throws io.github.jan.supabase.exceptions.RestException 코드가 존재하지 않거나 서버 오류.
     */
    suspend fun joinByInviteCode(code: String): String {
        val result = SupabaseProvider.client.postgrest.rpc(
            function = "join_couple_by_invite",
            parameters = buildJsonObject {
                put("p_code", code)
            },
        )
        return result.decodeAs<String>()
    }
}
