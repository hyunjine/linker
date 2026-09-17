package com.hyunjine.linker.adminweb.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.hyunjine.linker.adminweb.resources.Res
import com.hyunjine.linker.adminweb.resources.pretendard_bold
import com.hyunjine.linker.adminweb.resources.pretendard_medium
import com.hyunjine.linker.adminweb.resources.pretendard_regular
import com.hyunjine.linker.adminweb.resources.pretendard_semibold
import org.jetbrains.compose.resources.Font

/**
 * `:adminWeb` 전용 Pretendard 폰트 패밀리 로더.
 *
 * `:shared` 는 wasmJs 타깃이 없어 (`designsystem/theme/PretendardFontFamily.kt` 재사용 불가)
 * 동일 파일들을 `adminWeb/src/wasmJsMain/composeResources/font/` 에 카피하고 여기서 다시 노출한다.
 * 브라우저 Skia 는 시스템 폰트에 접근하지 못하므로 한글 글리프가 없는 기본 스택으로는
 * 모든 한글이 `□` 로 렌더링된다 (#290). 앱 부팅 시 이 패밀리를 `Typography` 및
 * `LocalTextStyle` 로 밀어 넣어야 한글이 정상 표시된다.
 *
 * @return Regular · Medium · SemiBold · Bold 4종을 담은 [FontFamily].
 */
@Composable
fun pretendardFontFamily(): FontFamily = FontFamily(
    Font(Res.font.pretendard_regular, weight = FontWeight.Normal),
    Font(Res.font.pretendard_medium, weight = FontWeight.Medium),
    Font(Res.font.pretendard_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.pretendard_bold, weight = FontWeight.Bold),
)
