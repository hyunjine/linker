package com.hyunjine.linker.designsystem.theme

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

/**
 * `public.users.calendar_color` 문자열 → 팔레트 Color 매핑.
 *
 * 값 형식:
 *  - 프리셋 id (`blue` / `mint` / ...): 하드코딩 팔레트 반환
 *  - 커스텀 hex (`#RRGGBB` · `#RRGGBBAA`): 파싱해 [Color] 반환 (사용자 #247 커스텀 컬러)
 *
 * 알 수 없는 값 · 파싱 실패는 [CalendarBlue] fallback (기존 동작 유지).
 */
fun calendarColorFor(id: String?): Color {
    if (id != null && id.startsWith('#')) {
        parseHexColor(id)?.let { return it }
    }
    return when (id) {
        "blue" -> CalendarBlue
        "mint" -> CalendarMint
        "green" -> CalendarGreen
        "yellow" -> CalendarYellow
        "orange" -> CalendarOrange
        "pink" -> CalendarPink
        "purple" -> CalendarPurple
        "gray" -> CalendarGray
        else -> CalendarBlue
    }
}

/** [id] 가 커스텀 hex 컬러 (`#RRGGBB` · `#RRGGBBAA`) 형식이면 true. 프리셋 id 와 구분용. */
fun isCustomHexColorId(id: String?): Boolean =
    id != null && id.startsWith('#') && parseHexColor(id) != null

/**
 * [Color] → "#RRGGBB" 문자열. 알파 채널은 무시. KMP 호환 (String.format 없이 직접 변환).
 * 커스텀 컬러 시트에 현재 프리셋 · 커스텀 hex 상관없이 통일된 hex 로 initialHex 를 넘길 때 사용.
 */
fun Color.toRgbHex(): String {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    fun Int.h2() = toString(16).padStart(2, '0').uppercase()
    return "#${r.h2()}${g.h2()}${b.h2()}"
}

/** "#RRGGBB" · "#RRGGBBAA" → [Color]. 잘못된 형식이면 null. 대소문자 무관. */
private fun parseHexColor(hex: String): Color? {
    val s = hex.removePrefix("#")
    if (s.length != 6 && s.length != 8) return null
    val v = s.toLongOrNull(16) ?: return null
    val a: Int
    val r: Int
    val g: Int
    val b: Int
    if (s.length == 8) {
        a = ((v shr 24) and 0xFF).toInt()
        r = ((v shr 16) and 0xFF).toInt()
        g = ((v shr 8) and 0xFF).toInt()
        b = (v and 0xFF).toInt()
    } else {
        a = 0xFF
        r = ((v shr 16) and 0xFF).toInt()
        g = ((v shr 8) and 0xFF).toInt()
        b = (v and 0xFF).toInt()
    }
    return Color(red = r, green = g, blue = b, alpha = a)
}

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
// 기념일 chip — CalendarPurple 계열. 공휴일 · 절기 · 개인 일정과 시각적으로 구분되는 유일한 톤.
// 후속 재설계에서 3카테고리 위계 (스케줄 · 할일 · 기념일) 를 다시 다듬을 때 팔레트도 함께 조정 예정 (#182).
val ChipAnniversaryBg = Color(0xFFEDE1FB)
val ChipAnniversaryText = Color(0xFF6D3AB2)

// 드로워 강조 버튼 배경 (기념일 설정 등)
val DrawerButtonBg = Color(0xFFF5F5F7)

// 드로워 표시 옵션 체크박스 (Figma #599CFF)
val DrawerCheckBlue = Color(0xFF599CFF)

// 드로워 하단 액션바 (기념일 · 에브리타임) 상단 구분선 (#327 · Figma #E7E7E7)
val DrawerBottomNavBorder = Color(0xFFE7E7E7)


// 할 일 내역 (#304) 미완료 체크박스 테두리 (Figma #AFAFB4)
val TaskCheckBorder = Color(0xFFAFAFB4)

// 정렬 드롭다운 메뉴 그림자 (#304 · 검정 12%)
val MenuShadow = Color(0x1F000000)
// 정렬 드롭다운 메뉴 외곽 1px 그림자 (#304 · 검정 4%)
val MenuOutlineShadow = Color(0x0A000000)
