package com.hyunjine.linker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.pretendard_bold
import linker.shared.generated.resources.pretendard_medium
import linker.shared.generated.resources.pretendard_regular
import linker.shared.generated.resources.pretendard_semibold
import org.jetbrains.compose.resources.Font

// Pretendard는 앱 전역에서 하나의 인스턴스만 필요하고 재구성 트리거 대상이 아니므로 static 사용.
// 미리 주입되지 않은 상태에서 소비하면 즉시 알리기 위해 error 사용.
val LocalPretendardFontFamily = staticCompositionLocalOf<FontFamily> {
    error("LocalPretendardFontFamily was not provided — wrap your content with ProvidePretendard { ... }")
}

@Composable
fun pretendardFontFamily(): FontFamily = FontFamily(
    Font(Res.font.pretendard_regular, weight = FontWeight.Normal),
    Font(Res.font.pretendard_medium, weight = FontWeight.Medium),
    Font(Res.font.pretendard_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.pretendard_bold, weight = FontWeight.Bold),
)

@Composable
fun ProvidePretendard(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPretendardFontFamily provides pretendardFontFamily(),
        content = content,
    )
}
