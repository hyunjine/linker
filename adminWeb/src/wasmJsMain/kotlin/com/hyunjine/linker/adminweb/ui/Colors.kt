package com.hyunjine.linker.adminweb.ui

import androidx.compose.ui.graphics.Color

/**
 * 관리자 콘솔 전용 팔레트.
 *
 * `:shared` 의 `designsystem/theme/Color.kt` 와 물리적으로 분리된 정의다. `:adminWeb` 는 wasmJs
 * 단독 모듈이라 `:shared` 를 데려오지 못하는 제약 (`docs/261-admin-push-console.md` §5) 때문에
 * 필요한 톤만 이곳에 재정의한다. 앱과 동일한 iOS 계열 톤을 유지해 시각적 일관성은 유지.
 *
 * 새 색이 필요하면 이 파일에만 추가하고 컴포저블에서는 import 로만 사용한다.
 */
internal object AdminColors {
    /** 페이지 전역 배경 — Figma `4010:63060` white/1. */
    val Background: Color = Color.White

    /** 카드 배경. 배경과 동일하지만 시각적 대비를 위해 별도 상수로 보존. */
    val CardBackground: Color = Color.White

    /** 카드 드롭 섀도우 색 — 검정 6% (Figma 0/8/32 6%). */
    val CardShadow: Color = Color.Black.copy(alpha = 0.06f)

    /** 입력 필드 배경 — iOS `secondarySystemBackground` 계열. */
    val FieldBackground: Color = Color(0xFFF5F5FA)

    /** 필드 placeholder / 보조 라벨 톤. */
    val PlaceholderText: Color = Color(0xFF7F7F86)

    /** 본문 최상위 텍스트 (0.9 black). */
    val TextPrimary: Color = Color.Black.copy(alpha = 0.9f)

    /** 필드 라벨용 텍스트 (0.7 black). */
    val TextLabel: Color = Color.Black.copy(alpha = 0.7f)

    /** 서브타이틀 · 부가 설명용 gray60. */
    val TextSubtle: Color = Color(0xFF8E8E93)

    /** 체크박스 미체크 스트로크 — iOS separator (60,60,67,0.35). */
    val CheckboxStroke: Color = Color(red = 60, green = 60, blue = 67, alpha = 90)

    /** 브랜드 액션 파랑 — iOS system blue. */
    val BrandBlue: Color = Color(0xFF0A84FF)

    /** 브랜드 파랑 위 텍스트. */
    val OnBrand: Color = Color.White

    /** 인라인 에러 텍스트 — iOS system red. */
    val Error: Color = Color(0xFFFF3B30)

    /** Top bar 하단 헤어라인 컬러. */
    val TopBarDivider: Color = Color(0xFFECECEE)
}
