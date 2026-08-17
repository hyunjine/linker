package com.hyunjine.linker.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 시스템 사진 라이브러리에서 이미지 한 장을 고르게 하는 launcher 를 기억한다.
 *
 * 반환된 람다를 호출하면 플랫폼별 사진 선택 UI 가 즉시 뜬다.
 * - iOS: `PHPickerViewController` (권한 필요 없음, iOS 14+)
 * - Android: 프리뷰 전용 no-op (실제 앱은 iOS만 배포)
 *
 * @param onImage 사용자가 이미지를 골랐을 때 [ImageBitmap] 으로 콜백.
 * 취소했거나 로드 실패 시 `null` 을 전달한다. 콜백은 항상 메인 스레드에서 호출된다.
 * @return 호출하면 사진 선택 UI 를 띄우는 람다.
 */
@Composable
expect fun rememberImagePicker(onImage: (ImageBitmap?) -> Unit): () -> Unit
