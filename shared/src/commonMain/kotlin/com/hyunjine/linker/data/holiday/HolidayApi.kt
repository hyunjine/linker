package com.hyunjine.linker.data.holiday

import com.hyunjine.linker.data.Secrets
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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

/** 하루짜리 공휴일 응답 항목. `locdate` 는 yyyyMMdd 정수. */
data class HolidayDto(
    val locdate: Int,
    val dateName: String,
    val isHoliday: Boolean,
)

/**
 * data.go.kr 특일정보 API 래퍼. 연 단위로 국경일 + 공휴일 + 대체공휴일을 가져온다.
 *
 * 응답 JSON 은 `response.body.items.item` 경로에 항목이 오는데, item 이 없으면 `items = ""` 로 오는
 * 이상한 관례가 있어 primitive 여부를 먼저 확인해야 한다 (한국 공공 API 흔한 함정).
 */
class HolidayApi(
    private val client: HttpClient = defaultClient(),
) {
    suspend fun fetchYear(year: Int): List<HolidayDto> {
        // solMonth 를 안 넘기면 해당 연 전체를 반환. numOfRows 는 넉넉히 (연 최대 30건 정도).
        val response: JsonElement = client
            .get("https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo") {
                parameter("serviceKey", Secrets.HolidayApiKey)
                parameter("solYear", year)
                parameter("numOfRows", 100)
                parameter("pageNo", 1)
                parameter("_type", "json")
            }
            .body()
        return parseItems(response)
    }

    private fun parseItems(root: JsonElement): List<HolidayDto> {
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

    private fun JsonObject.toDto(): HolidayDto = HolidayDto(
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
