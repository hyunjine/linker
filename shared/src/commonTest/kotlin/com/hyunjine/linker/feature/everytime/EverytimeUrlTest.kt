package com.hyunjine.linker.feature.everytime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EverytimeUrlTest {

    @Test
    fun parses_full_https_url() {
        assertEquals(
            "MXbJmcQUOSymAb6IHAcE",
            EverytimeUrl.parseIdentifier("https://everytime.kr/@MXbJmcQUOSymAb6IHAcE"),
        )
    }

    @Test
    fun parses_http_url() {
        assertEquals(
            "abc12345",
            EverytimeUrl.parseIdentifier("http://everytime.kr/@abc12345"),
        )
    }

    @Test
    fun parses_bare_domain_url() {
        assertEquals(
            "abc12345",
            EverytimeUrl.parseIdentifier("everytime.kr/@abc12345"),
        )
    }

    @Test
    fun parses_raw_identifier() {
        assertEquals(
            "MXbJmcQUOSymAb6IHAcE",
            EverytimeUrl.parseIdentifier("MXbJmcQUOSymAb6IHAcE"),
        )
    }

    @Test
    fun strips_trailing_slash_and_query() {
        assertEquals(
            "abcd1234",
            EverytimeUrl.parseIdentifier("https://everytime.kr/@abcd1234/?utm=x"),
        )
    }

    @Test
    fun rejects_too_short() {
        assertNull(EverytimeUrl.parseIdentifier("https://everytime.kr/@abc"))
    }

    @Test
    fun rejects_invalid_char() {
        assertNull(EverytimeUrl.parseIdentifier("https://everytime.kr/@bad-id!"))
    }

    @Test
    fun rejects_empty() {
        assertNull(EverytimeUrl.parseIdentifier(""))
        assertNull(EverytimeUrl.parseIdentifier("   "))
    }

    @Test
    fun builds_share_url() {
        assertEquals(
            "https://everytime.kr/@abc123",
            EverytimeUrl.buildShareUrl("abc123"),
        )
    }
}
