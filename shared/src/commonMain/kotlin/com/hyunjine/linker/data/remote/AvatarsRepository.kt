package com.hyunjine.linker.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// avatars Storage 버킷 업로드 헬퍼. RLS 로 {auth.uid()}/* 만 write 가능하므로
// 경로를 {uid}/{timestamp}.jpg 로 강제. public 버킷이라 return URL 은 CDN 을 통해 바로 접근.
// timestamp 를 파일명에 넣어 매 저장마다 새 URL 이 발급됨 - Coil / AsyncImage 캐시가 이전
// 이미지를 계속 보여주는 문제 자연스레 해결.
object AvatarsRepository {
    private const val BUCKET = "avatars"

    // JPEG 바이트를 avatars 버킷에 업로드하고 public URL 반환.
    // (원본 PNG 는 iPhone 카메라 사진 기준 5MB 를 쉽게 초과하므로 다운스케일 + JPEG 로 변경 · #139)
    @OptIn(ExperimentalTime::class)
    suspend fun uploadJpeg(bytes: ByteArray): String {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: error("로그인되지 않은 상태에서 avatar 업로드 시도")
        val ts = Clock.System.now().toEpochMilliseconds()
        val path = "$uid/$ts.jpg"
        val bucket = SupabaseProvider.client.storage.from(BUCKET)
        bucket.upload(path, bytes) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        return bucket.publicUrl(path)
    }
}
