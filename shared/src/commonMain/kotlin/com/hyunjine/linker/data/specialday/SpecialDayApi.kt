package com.hyunjine.linker.data.specialday

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 하루짜리 특일 응답 항목. `locdate` 는 yyyyMMdd 정수. */
data class SpecialDayDto(
    val locdate: Int,
    val dateName: String,
    val isHoliday: Boolean,
)

/**
 * 한국 공휴일 API 래퍼. [SpecialDayKind] 로 종류를 구분해 연 단위로 fetch.
 *
 * 소스: [nager.date PublicHolidays](https://date.nager.at/api/v3/PublicHolidays/{year}/KR).
 * 이전에 쓰던 `data.go.kr SpcdeInfoService` 는 2028년까지만 데이터가 등록되어 있어 2029년 이후를
 * 커버하지 못하는 문제가 있었다 (#14). nager.date 는 미래 연도 무제한, 인증 불필요, 무료.
 * 다만 nager 는 공휴일만 제공하고 24절기는 없으므로 [SpecialDayKind.SolarTerm] 은 빈 리스트를 반환한다.
 */
class SpecialDayApi(
    private val client: HttpClient = defaultClient(),
) {
    suspend fun fetchYear(year: Int, kind: SpecialDayKind): List<SpecialDayDto> {
        // 24절기 소스는 아직 미확보. 향후 별도 소스 (KASI OpenAPI or 자체 계산) 도입 시 여기 분기 추가.
        if (kind == SpecialDayKind.SolarTerm) return emptyList()

        val response: List<NagerHoliday> = client
            .get("https://date.nager.at/api/v3/PublicHolidays/$year/KR")
            .body()
        return response.map { it.toDto() }
    }

    /** nager.date PublicHolidays 응답 항목. 우리가 쓰는 필드만 매핑. */
    @Serializable
    private data class NagerHoliday(
        val date: String,          // "YYYY-MM-DD"
        val localName: String,     // 한국어 이름 (예: "설날", "광복절")
        val name: String = "",     // 영문 이름. 우선순위 낮음
    )

    private fun NagerHoliday.toDto(): SpecialDayDto = SpecialDayDto(
        // "2030-01-01" → 20300101
        locdate = date.replace("-", "").toInt(),
        dateName = localName,
        // nager 는 공휴일만 반환. 대체공휴일도 별도 항목으로 내려오므로 전부 true.
        isHoliday = true,
    )

    companion object {
        private fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }
}
