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

/**
 * `:adminWeb` 콘솔 전용 확장 팔레트.
 *
 * [AdminColors] 와 겹치는 값은 `AdminColors` 를 재노출해 정의 이중화를 피한다. 배지 · 토스트 등
 * 콘솔에서만 쓰는 톤은 이 오브젝트에서 새로 정의. 후속 리팩터에서 하나의 오브젝트로 통합할 수 있으나
 * 현재는 파일 하나에 두 집합이 공존해도 무해하므로 기능 완성을 우선.
 */
internal object Colors {
    /** iOS 시스템 블루. 브랜드 pill · 링크 · primary 액션. */
    val PrimaryBlue = AdminColors.BrandBlue

    /** primary 컨텐츠 위 텍스트 (pill 라벨 등). */
    val OnPrimary = AdminColors.OnBrand

    /** 콘솔 전체 배경. #F5F5FA — iOS 시스템 배경과 동일 톤. */
    val Background = Color(0xFFF5F5FA)

    /** 카드 · 패널 · 인풋 필드 안쪽 흰색 표면. */
    val Surface = Color.White

    /** 인풋 · 검색창 배경. 회색이 살짝 도는 아주 밝은 회색. */
    val FieldFill = AdminColors.FieldBackground

    /** 본문 텍스트. 완전 검정 대신 살짝 어두운 회흑색. */
    val TextPrimary = Color(0xFF111111)

    /** 보조 텍스트 · 라벨. */
    val TextSecondary = Color(0xFF6B6B70)

    /** 힌트 · placeholder · 아주 옅은 텍스트. */
    val TextTertiary = Color(0xFF9B9BA1)

    /** 경계선 · 하단 hairline. */
    val Divider = Color(0xFFE5E5EA)

    /** 체크박스 · 아바타 배경 등 살짝 진한 grey. */
    val NeutralFill = Color(0xFFEDEDF2)

    /** 카운트 초과 알림 등 파괴적 컬러. */
    val Danger = Color(0xFFE5484D)

    /** 성공 스낵바 배경 (다크). */
    val ToastSuccess = Color(0xFF16A34A)

    /** 실패 스낵바 배경 (다크). */
    val ToastError = Color(0xFFDC2626)

    /** iOS 배지 톤 (연한 회색 배경 + 진한 텍스트). */
    val PlatformIOSBg = Color(0xFFE5E5EA)
    val PlatformIOSText = Color(0xFF1C1C1E)

    /** Android 배지 톤 (초록 계열). */
    val PlatformAndroidBg = Color(0xFFDFF5E3)
    val PlatformAndroidText = Color(0xFF2E7D32)
}
