package com.hyunjine.linker.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * [ImageBitmap] 을 아바타용으로 다운스케일 · JPEG 인코딩해서 반환.
 *  - 최대 변 길이 512px (프로필 사진은 이 정도면 충분, 44dp ~ 100dp 로만 렌더)
 *  - JPEG 품질 85 (알파 필요 없고 파일 크기가 관건)
 *  - 원본 aspect ratio 유지
 *
 * 이유: iPhone 카메라 원본 PNG 는 8~15MB 로 avatars 버킷 5MB 제한을 쉽게 초과.
 * 다운스케일 + JPEG 로 보통 50~150KB 로 줄어듦.
 */
expect fun ImageBitmap.encodeAvatarJpeg(): ByteArray
