package com.hyunjine.linker.data.remote

import kotlin.random.Random

/**
 * 클라이언트가 먼저 만들어야 하는 id 용 UUID v4.
 *
 * platform 의존 없이 kotlinx.uuid 도 안 쓰고 random 16 bytes 를 hex 로 만든다.
 * Supabase 는 문자열이면 `uuid` 컬럼에 그대로 들어감 (postgres 가 파싱).
 *
 * 쓰는 곳: [SchedulesRepository] 반복 시리즈 id, [CouplePhotosRepository] 사진 id
 * (사진은 Storage 경로에 id 가 들어가서 insert 응답을 기다릴 수 없다).
 */
internal fun randomUuid(): String {
    val bytes = Random.Default.nextBytes(16)
    // RFC 4122 v4 markers
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    val hex = bytes.joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
        "${hex.substring(16, 20)}-${hex.substring(20)}"
}
