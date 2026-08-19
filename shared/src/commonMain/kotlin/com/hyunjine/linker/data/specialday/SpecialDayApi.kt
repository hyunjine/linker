package com.hyunjine.linker.data.specialday

import com.hyunjine.linker.data.Secrets
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.decodeURLQueryComponent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 하루짜리 특일 응답 항목. `locdate` 는 yyyyMMdd 정수. */
data class SpecialDayDto(
    val locdate: Int,
    val dateName: String,
    val isHoliday: Boolean,
)

/**
 * data.go.kr 특일정보(SpcdeInfoService) API 래퍼. [SpecialDayKind] 로 엔드포인트를 골라 연 단위로 fetch.
 *
 * 응답 JSON 은 `response.body.items.item` 경로에 항목이 오는데, item 이 없으면 `items = ""` 로 오는
 * 이상한 관례가 있어 primitive 여부를 먼저 확인해야 한다 (한국 공공 API 흔한 함정).
 *
 * 서비스 키 이중 인코딩 주의: `local.properties` 의 키는 이미 URL 인코딩 (%2F/%3D) 상태라
 * 그대로 `parameter(...)` 로 넘기면 Ktor 가 다시 인코딩 (%252F) 해서 유효하지 않은 키가 됨.
 * 한 번 디코드해서 raw 로 만든 뒤 Ktor 가 한 번 인코딩하도록 한다.
 */
class SpecialDayApi(
    private val client: HttpClient = defaultClient(),
) {
    suspend fun fetchYear(year: Int, kind: SpecialDayKind): List<SpecialDayDto> {
        val serviceKey = Secrets.HolidayApiKey.decodeURLQueryComponent(plusIsSpace = true)
        val response: JsonElement = client
            .get("https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/${kind.endpoint}") {
                parameter("serviceKey", serviceKey)
                parameter("solYear", year)
                parameter("numOfRows", 100)
                parameter("pageNo", 1)
                parameter("_type", "json")
            }
            .body()
        return parseItems(response)
    }

    private fun parseItems(root: JsonElement): List<SpecialDayDto> {
        val body = root.jsonObject["response"]
            ?.jsonObject?.get("body")
            ?.jsonObject ?: return emptyList()
        val items = body["items"] ?: return emptyList()
        if (items is JsonPrimitive) return emptyList() // empty case: ""
        val itemNode = items.jsonObject["item"] ?: return emptyList()
        return when (itemNode) {
            is JsonObject -> listOf(itemNode.toDto())
            else -> itemNode.jsonArray.mapNotNull { (it as? JsonObject)?.toDto() }
        }
    }

    private fun JsonObject.toDto(): SpecialDayDto = SpecialDayDto(
        locdate = this["locdate"]?.jsonPrimitive?.intOrNull ?: 0,
        dateName = this["dateName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        isHoliday = this["isHoliday"]?.jsonPrimitive?.contentOrNull == "Y",
    )

    companion object {
        private fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }
}
