package com.hyunjine.linker.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
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
     * 파트너 조인 여부까지 포함한 full couple row. [linkedAt] 이 non-null 이면 두 명 매칭 완료.
     */
    @Serializable
    data class CoupleFull(
        val id: String,
        @SerialName("invite_code") val inviteCode: String,
        @SerialName("linked_at") val linkedAt: String? = null,
    )

    /**
     * 현재 유저의 커플을 조회한다. 없으면 새 커플을 만들고 자신을 첫 멤버로 join.
     * 재진입 시 같은 커플이 반환되어 idempotent.
     */
    suspend fun createOrGetMyCouple(): MyCouple {
        val result = SupabaseProvider.client.postgrest.rpc("create_my_couple")
        return result.decodeList<MyCouple>().first()
    }

    /** id 로 couple row 조회. RLS 로 내가 속한 커플만 반환됨. 없으면 null. */
    suspend fun getCoupleById(id: String): CoupleFull? =
        SupabaseProvider.client.from("couples")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<CoupleFull>()

    /**
     * 현재 유저의 couple_id 만 조회. **생성하지 않음** — 부트스트랩 라우팅에서
     * "커플이 있는지" 판단할 때 사용. 없거나 세션 없으면 null.
     */
    suspend fun myCoupleIdOrNull(): String? {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return null
        return SupabaseProvider.client.from("couple_members")
            .select { filter { eq("user_id", uid) } }
            .decodeList<CoupleMemberRow>()
            .firstOrNull()
            ?.coupleId
    }

    @Serializable
    private data class CoupleMemberRow(@SerialName("couple_id") val coupleId: String)

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
