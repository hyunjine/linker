package com.hyunjine.linker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.pretendard_bold
import linker.shared.generated.resources.pretendard_medium
import linker.shared.generated.resources.pretendard_regular
import linker.shared.generated.resources.pretendard_semibold
import org.jetbrains.compose.resources.Font

@Composable
fun pretendardFontFamily(): FontFamily = FontFamily(
    Font(Res.font.pretendard_regular, weight = FontWeight.Normal),
    Font(Res.font.pretendard_medium, weight = FontWeight.Medium),
    Font(Res.font.pretendard_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.pretendard_bold, weight = FontWeight.Bold),
)
