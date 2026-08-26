package com.hyunjine.linker.data.remote

import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `public.couple_anniversaries` CRUD. RLS `anniversaries_all_in_my_couple` 로
 * 내 커플 기념일만 접근 가능. 전용 UI 는 아직 없어 리포지토리만 준비.
 */
object AnniversariesRepository {

    @Serializable
    data class Row(
        val id: String,
        @SerialName("couple_id") val coupleId: String,
        val title: String,
        val date: String,   // ISO
        @SerialName("repeat_yearly") val repeatYearly: Boolean,
    )

    /**
     * INSERT 전용 payload. `Row` 를 그대로 쓰면 `id = "00000000-..."` placeholder 가 서버에 그대로
     * 기록되어 (Postgres DEFAULT 는 컬럼 명시 시 발동하지 않음) PK 위반. id 필드를 빼서 서버
     * `gen_random_uuid()` default 가 발동하도록 한다.
     */
    @Serializable
    data class InsertPayload(
        @SerialName("couple_id") val coupleId: String,
        val title: String,
        val date: String,
        @SerialName("repeat_yearly") val repeatYearly: Boolean,
    )

    /** 내 커플의 기념일 목록 (date 오름차순). 커플 없으면 빈 리스트. */
    suspend fun list(): List<Row> {
        val coupleId = SchedulesRepository.myCoupleId() ?: return emptyList()
        return SupabaseProvider.client.from("couple_anniversaries")
            .select {
                filter { eq("couple_id", coupleId) }
                order("date", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }
            .decodeList<Row>()
    }

    /** 제목 부분일치 (대소문자 무시) 검색. 빈 문자열은 빈 결과. */
    suspend fun search(query: String): List<Row> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val coupleId = SchedulesRepository.myCoupleId() ?: return emptyList()
        return SupabaseProvider.client.from("couple_anniversaries")
            .select {
                filter {
                    eq("couple_id", coupleId)
                    ilike("title", "%$trimmed%")
                }
                order("date", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }
            .decodeList<Row>()
    }

    /** 기념일 추가. 반환값은 새 row id. */
    suspend fun create(title: String, date: LocalDate, repeatYearly: Boolean = true): String {
        val coupleId = SchedulesRepository.myCoupleId()
            ?: error("커플에 속하지 않은 유저가 기념일 저장 시도")
        val inserted = SupabaseProvider.client.from("couple_anniversaries")
            .insert(
                InsertPayload(
                    coupleId = coupleId,
                    title = title,
                    date = date.toString(),
                    repeatYearly = repeatYearly,
                ),
            ) { select() }
            .decodeSingle<Row>()
        return inserted.id
    }

    suspend fun delete(id: String) {
        SupabaseProvider.client.from("couple_anniversaries").delete {
            filter { eq("id", id) }
        }
    }
}
