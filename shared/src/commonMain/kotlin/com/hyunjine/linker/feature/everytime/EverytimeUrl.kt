package com.hyunjine.linker.feature.everytime

/**
 * 에브리타임 시간표 공유 URL 파서 (#306).
 *
 * 지원 입력:
 *  - `https://everytime.kr/@MXbJmcQUOSymAb6IHAcE`
 *  - `http://everytime.kr/@MXbJmcQUOSymAb6IHAcE`
 *  - `everytime.kr/@MXbJmcQUOSymAb6IHAcE`
 *  - 그냥 `MXbJmcQUOSymAb6IHAcE` 만 붙여넣은 경우
 *
 * 반환은 마지막 `@` 뒤 identifier 문자열. 형식은 base62 (`^[A-Za-z0-9]{4,32}$`).
 * 유효하지 않으면 null.
 */
object EverytimeUrl {

    private val IdentifierRegex = Regex("^[A-Za-z0-9]{4,32}$")

    /**
     * 사용자가 붙여넣은 자유 형식 텍스트에서 identifier 만 뽑는다.
     * 공백 · 쿼리스트링 · 트레일링 슬래시는 무시.
     */
    fun parseIdentifier(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // `@` 가 있으면 마지막 `@` 뒤를 identifier 후보로.
        val atIdx = trimmed.lastIndexOf('@')
        val candidate = if (atIdx >= 0) trimmed.substring(atIdx + 1) else trimmed

        // 쿼리스트링 · 프래그먼트 · 트레일링 슬래시 제거.
        val head = candidate
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
        return head.takeIf { IdentifierRegex.matches(it) }
    }

    /** identifier 만 저장돼 있을 때 공유 URL 을 재조립. */
    fun buildShareUrl(identifier: String): String = "https://everytime.kr/@$identifier"
}
