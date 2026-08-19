package com.hyunjine.linker.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// 앱 전역 팔레트. 새 색이 필요하면 여기서만 정의하고 screen 파일에서는 import 만 한다.

// 기본 배경/텍스트
val Background = Color.White
val TextPrimary = Color(0xFF1A1A1A)

// 로고 그라디언트 (Figma: from #FF7E86 → to #FFB47A, horizontal)
val LogoGradientStart = Color(0xFFFF7E86)
val LogoGradientEnd = Color(0xFFFFB47A)
val LogoGradient: Brush = Brush.horizontalGradient(
    colors = listOf(LogoGradientStart, LogoGradientEnd),
)

// 카카오 로그인 버튼
val KakaoYellow = Color(0xFFFEE500)
val KakaoLabel = Color(0xFF3C1E1E)

// iOS 시스템 톤 (프로필 편집 등에서 사용)
val SurfaceGray = Color(0xFFF2F2F7)      // 화면 배경
val SurfaceCard = Color.White            // 카드 배경
val TextSecondary = Color(0xFF8E8E93)    // 보조 텍스트
val TextTertiary = Color(0xFF6D6D73)     // 섹션 라벨
val Separator = Color(0xFFC6C6C8)        // 리스트/카드 구분선
val SeparatorGrouped = Color(0xFFD9D9DE) // iOS 26 modal-style 카드 내부 세퍼레이터 (더 옅음)
val Chevron = Color(0xFFC6C6C8)          // > 화살표
val SegmentTrack = Color(0xFFE8E8ED)     // iOS 26 세그먼트 컨트롤 트랙 배경
val PlaceholderText = Color(0xFF999999)  // iOS 26 폼 필드 placeholder
val AvatarPlaceholderBg = Color(0xFFE5E5EA)
val AvatarPlaceholderFg = Color(0xFF8E8E93)

// 브랜드/인터랙션 (iOS 계열 파랑)
val PrimaryBlue = Color(0xFF008AFF)
val OnPrimary = Color.White

// 내 캘린더 색상 팔레트
val CalendarBlue = Color(0xFF008AFF)
val CalendarMint = Color(0xFF4ECDC4)
val CalendarGreen = Color(0xFF34C759)
val CalendarYellow = Color(0xFFFFCC00)
val CalendarOrange = Color(0xFFFF9500)
val CalendarPink = Color(0xFFFF375F)
val CalendarPurple = Color(0xFFAF52DE)
val CalendarGray = Color(0xFF8E8E93)

// 메인 캘린더 화면 (월 그리드)
val CalendarWeekdayText = Color(0xFF1A1A1A) // 평일 숫자 (TextPrimary 와 동일하지만 시맨틱 구분)
val CalendarSunday = Color(0xFFF0474D)      // 일요일/공휴일 숫자
val CalendarSaturday = Color(0xFF1A7AFA)    // 토요일 숫자
val CalendarLunarText = Color(0xFF9999A6)   // 셀 안 음력 날짜
val CalendarTodayCircle = Color(0xFF1A1A1A) // 오늘 원 배경 (검정)
val CalendarTodayText = Color.White         // 오늘 숫자 (흰색)

// 이벤트 chip (하루 셀 안 표시)
val ChipHolidayBg = Color(0xFFFCE8EB)   // 공휴일 배경
val ChipHolidayText = Color(0xFFDE4752) // 공휴일 글자
val ChipSeasonBg = Color(0xFFF0F0F2)    // 절기 배경
val ChipSeasonText = Color(0xFF6B6B75)  // 절기 글자
val ChipPersonalBg = Color(0xFFFADEE3)  // 개인 일정 배경
val ChipPersonalText = Color(0xFFBF404D) // 개인 일정 글자
