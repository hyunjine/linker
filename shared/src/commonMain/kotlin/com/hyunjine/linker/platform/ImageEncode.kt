package com.hyunjine.linker.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * [ImageBitmap] 을 PNG 바이트로 인코딩. Supabase Storage 업로드에 사용.
 * 사용자가 큰 사진을 골랐어도 avatars 버킷 file_size_limit (5MB) 을 넘지 않도록 UI 층에서 이미
 * 리사이즈됐다고 가정 (현재는 photo picker 자체가 원본 반환 → 필요 시 별도 다운스케일).
 */
expect fun ImageBitmap.encodeToPngBytes(): ByteArray
