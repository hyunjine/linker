package com.hyunjine.linker.data.remote

import androidx.compose.ui.graphics.ImageBitmap
import com.hyunjine.linker.platform.PhotoSpec
import com.hyunjine.linker.platform.encodePhotoJpeg
import com.hyunjine.linker.platform.encodePhotoThumbJpeg
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.hours

/**
 * `public.couple_photos` + private `couple-photos` 버킷.
 *
 * 사진은 날짜 (`taken_on`) 에 속하고, 같은 커플만 볼 수 있으며, 올린 사람만 지울 수 있다
 * (RLS 가 강제 — 클라이언트는 UI 만 가린다).
 *
 * 파일 경로 규칙 (버킷 정책이 첫 세그먼트로 소유 커플을 판정하므로 반드시 지켜야 한다):
 *  - 원본:   `{coupleId}/{photoId}.jpg`
 *  - 썸네일: `{coupleId}/{photoId}_thumb.jpg`
 *
 * avatars 버킷과 달리 **private** 이라 public URL 이 없다. 읽을 때마다 [signedUrls] 로 발급받고,
 * 발급 URL 은 매번 토큰이 달라지므로 이미지 캐시 키는 URL 이 아니라 `storage_path` 로 고정해야 한다
 * (`feature/photo/PhotoImage` 참고).
 */
object CouplePhotosRepository {

    /** 사진 버킷 id. 마이그레이션 `20261103054434__couple_photos.sql` 과 동일해야 한다. */
    const val BUCKET = "couple-photos"

    /** signed URL 유효 기간. 그리드를 한참 띄워 둬도 만료되지 않을 만큼 넉넉하게. */
    private val SIGNED_URL_TTL = 1.hours

    @Serializable
    data class Row(
        val id: String,
        @SerialName("couple_id") val coupleId: String,
        @SerialName("uploaded_by") val uploadedBy: String,
        @SerialName("taken_on") val takenOn: String,        // ISO date
        @SerialName("storage_path") val storagePath: String,
        @SerialName("thumb_path") val thumbPath: String,
        @SerialName("created_at") val createdAt: String,
    )

    /**
     * INSERT payload. `id` 는 Storage 경로에 들어가야 해서 **클라이언트가 먼저 만든다**
     * (서버 DEFAULT 를 쓰면 insert 응답을 받기 전엔 경로를 만들 수 없다). `created_at` 은 서버 DEFAULT.
     */
    @Serializable
    private data class InsertPayload(
        val id: String,
        @SerialName("couple_id") val coupleId: String,
        @SerialName("uploaded_by") val uploadedBy: String,
        @SerialName("taken_on") val takenOn: String,
        @SerialName("storage_path") val storagePath: String,
        @SerialName("thumb_path") val thumbPath: String,
    )

    /** 내 커플의 전체 사진 (최신 날짜 먼저, 같은 날짜면 나중에 올린 것 먼저). 커플 없으면 빈 리스트. */
    suspend fun list(): List<Row> {
        val coupleId = SchedulesRepository.myCoupleId() ?: return emptyList()
        return SupabaseProvider.client.from("couple_photos")
            .select {
                filter { eq("couple_id", coupleId) }
                order("taken_on", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Row>()
    }

    /** 특정 날짜의 사진 (나중에 올린 것 먼저). 날짜 상세 시트의 가로 목록용. */
    suspend fun listOn(date: LocalDate): List<Row> {
        val coupleId = SchedulesRepository.myCoupleId() ?: return emptyList()
        return SupabaseProvider.client.from("couple_photos")
            .select {
                filter {
                    eq("couple_id", coupleId)
                    eq("taken_on", date.toString())
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Row>()
    }

    /** 사진 한 개. 전체 보기 화면이 id 로 진입할 때 사용. 없거나 권한 없으면 null. */
    suspend fun findById(id: String): Row? =
        SupabaseProvider.client.from("couple_photos")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<Row>()

    /**
     * 사진 업로드. 고른 이미지를 규격대로 두 번 인코딩해 (원본 · 썸네일) 올린 뒤 메타 row 를 넣는다.
     *
     * 순서가 "파일 먼저, row 나중" 인 이유: row 를 먼저 넣으면 업로드가 실패했을 때 파일 없는 row 가
     * 그리드에 깨진 칸으로 남는다. 반대로 row insert 가 실패하면 올린 파일을 best-effort 로 되지운다.
     *
     * @param image 사용자가 고른 이미지.
     * @param takenOn 사진을 붙일 날짜.
     * @return 생성된 사진 id.
     * @throws IllegalStateException 커플 미연결 · 세션 없음 · 5MB 초과.
     */
    suspend fun upload(image: ImageBitmap, takenOn: LocalDate): String {
        val coupleId = SchedulesRepository.myCoupleId()
            ?: error("커플에 속하지 않은 상태에서 사진 업로드 시도")
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: error("로그인되지 않은 상태에서 사진 업로드 시도")

        val original = image.encodePhotoJpeg()
        val thumb = image.encodePhotoThumbJpeg()
        if (original.size > PhotoSpec.MAX_FILE_BYTES) {
            error("사진이 너무 큽니다 (${original.size / 1024}KB · 상한 ${PhotoSpec.MAX_FILE_BYTES / 1024}KB)")
        }

        // couple_photos.id 는 서버 default (gen_random_uuid) 가 있지만, Storage 경로에 id 가 들어가서
        // insert 응답을 기다릴 수 없다 → 클라이언트가 먼저 만든다. 컬럼이 uuid 타입이므로 반드시 UUID 형식
        // (schedules 의 series_id 와 같은 [randomUuid]).
        val photoId = randomUuid()
        val storagePath = "$coupleId/$photoId.jpg"
        val thumbPath = "$coupleId/${photoId}_thumb.jpg"
        val bucket = SupabaseProvider.client.storage.from(BUCKET)

        bucket.upload(storagePath, original) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        runCatching {
            bucket.upload(thumbPath, thumb) {
                upsert = false
                contentType = ContentType.Image.JPEG
            }
        }.onFailure {
            runCatching { bucket.delete(listOf(storagePath)) }
            throw it
        }

        runCatching {
            SupabaseProvider.client.from("couple_photos").insert(
                InsertPayload(
                    id = photoId,
                    coupleId = coupleId,
                    uploadedBy = uid,
                    takenOn = takenOn.toString(),
                    storagePath = storagePath,
                    thumbPath = thumbPath,
                ),
            )
        }.onFailure {
            // row 가 없으면 영원히 참조되지 않는 파일 → 과금만 되는 orphan. 지우고 실패를 올린다.
            runCatching { bucket.delete(listOf(storagePath, thumbPath)) }
            throw it
        }

        return photoId
    }

    /**
     * 사진 삭제. DB row 와 Storage 파일 2개 (원본 · 썸네일) 를 모두 지운다.
     * row 를 먼저 지우면 (RLS 로 올린 사람만 통과) 그 뒤 파일 삭제가 실패해도 UI 에선 바로 사라진다.
     */
    suspend fun delete(row: Row) {
        SupabaseProvider.client.from("couple_photos").delete {
            filter { eq("id", row.id) }
        }
        SupabaseProvider.client.storage.from(BUCKET)
            .delete(listOf(row.storagePath, row.thumbPath))
    }

    /**
     * private 버킷 경로들에 대한 signed URL 발급.
     *
     * @return `storage 경로 → signed URL`. 발급 실패한 경로는 맵에서 빠진다.
     */
    suspend fun signedUrls(paths: List<String>): Map<String, String> {
        if (paths.isEmpty()) return emptyMap()
        return SupabaseProvider.client.storage.from(BUCKET)
            .createSignedUrls(SIGNED_URL_TTL, paths.distinct())
            .filter { it.error == null }
            .associate { it.path to it.signedURL }
    }
}
