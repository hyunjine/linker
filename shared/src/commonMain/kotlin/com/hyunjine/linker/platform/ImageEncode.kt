package com.hyunjine.linker.platform

import androidx.compose.ui.graphics.ImageBitmap

/** 아바타 인코딩 규격 — 프로필 사진은 44dp ~ 100dp 로만 렌더해서 512px 이면 충분. */
private const val AVATAR_MAX_DIMENSION = 512

/** JPEG 품질. 알파가 필요 없고 파일 크기가 관건이라 85 로 고정. */
const val JPEG_QUALITY = 85

/**
 * 사진첩 인코딩 규격. 버킷 정책 (`allowed_mime_types = image/jpeg`, 5MB) 과 맞춰 둔다.
 */
object PhotoSpec {
    /** 원본 긴 변 최대 px. */
    const val ORIGINAL_MAX_DIMENSION = 2048

    /** 썸네일 긴 변 최대 px (사진첩 3열 그리드 · 날짜 상세 가로 목록). */
    const val THUMB_MAX_DIMENSION = 400

    /** 버킷 `file_size_limit` 과 동일한 파일당 상한 (byte). */
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
}

/**
 * [ImageBitmap] 을 긴 변 [maxDimension] 이하로 다운스케일한 뒤 JPEG 로 인코딩한다.
 * 원본 aspect ratio 는 유지하고, 이미 더 작으면 그대로 인코딩만 한다.
 *
 * 이유: iPhone 카메라 원본 PNG 는 8~15MB 로 Storage 5MB 제한을 쉽게 초과.
 * 다운스케일 + JPEG 로 아바타는 50~150KB, 사진첩 원본은 보통 400~700KB 로 줄어듦.
 */
expect fun ImageBitmap.encodeJpeg(maxDimension: Int, quality: Int = JPEG_QUALITY): ByteArray

/**
 * 아바타용 JPEG 인코딩. [encodeJpeg] 에 아바타 규격을 넣은 얇은 래퍼 (#139).
 */
fun ImageBitmap.encodeAvatarJpeg(): ByteArray = encodeJpeg(AVATAR_MAX_DIMENSION)

/** 사진첩 원본용 JPEG 인코딩. */
fun ImageBitmap.encodePhotoJpeg(): ByteArray = encodeJpeg(PhotoSpec.ORIGINAL_MAX_DIMENSION)

/** 사진첩 썸네일용 JPEG 인코딩. */
fun ImageBitmap.encodePhotoThumbJpeg(): ByteArray = encodeJpeg(PhotoSpec.THUMB_MAX_DIMENSION)
