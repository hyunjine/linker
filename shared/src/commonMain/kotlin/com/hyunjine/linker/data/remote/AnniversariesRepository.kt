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

    /** 기념일 추가. 반환값은 새 row id. */
    suspend fun create(title: String, date: LocalDate, repeatYearly: Boolean = true): String {
        val coupleId = SchedulesRepository.myCoupleId()
            ?: error("커플에 속하지 않은 유저가 기념일 저장 시도")
        val inserted = SupabaseProvider.client.from("couple_anniversaries")
            .insert(
                Row(
                    id = "00000000-0000-0000-0000-000000000000",
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
