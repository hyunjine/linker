package com.hyunjine.linker.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * 앱 전역 테마 래퍼. `MaterialTheme` (material3 default) + `ProvidePretendard` 를 한 번에 감싼다.
 *
 * App 루트 · 프리뷰 · 스탠드얼론 컴포저블 컨텍스트에서 사용. 개별 화면 · 컴포넌트가 두 번씩
 * material + pretendard 를 감쌀 필요 없이 이 래퍼 하나로 통일.
 */
@Composable
fun LinkerTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        ProvidePretendard(content = content)
    }
}
