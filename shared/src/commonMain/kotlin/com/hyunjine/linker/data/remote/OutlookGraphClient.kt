package com.hyunjine.linker.data.remote

import com.hyunjine.linker.auth.OutlookAuthClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Microsoft Graph API 클라이언트 (v1.0 `/me/calendarview` · `/me/events`).
 *
 * Bearer 토큰은 [auth] 에서 매 요청 시 [OutlookAuthClient.accessToken] 로 fresh 발급받아
 * 헤더에 실는다. 토큰 만료는 SDK 가 refresh_token 으로 자동 갱신하고, refresh 도 실패하면
 * null 반환 → 각 호출은 401 대신 [Result.failure] 로 조기 종료.
 *
 * timezone: 클라이언트가 로컬 시간 문자열을 그대로 다루기 위해 `Prefer: outlook.timezone`
 * 헤더에 사용자 로컬 TZ 를 실어 서버가 그 TZ 기준으로 start/end 를 반환하도록 유도.
 */
class OutlookGraphClient(
    private val auth: OutlookAuthClient,
    private val client: HttpClient = defaultClient(),
    private val timeZone: String = "Asia/Seoul",
) {

    /**
     * `/me/calendarview` — 지정 date range 내의 모든 (반복 시리즈 인스턴스 포함) 이벤트.
     * Graph 는 series master 하나만 주지 않고 매 발생일마다 별도 인스턴스로 확장해 반환.
     * @param startIso `2026-09-01T00:00:00` 같은 ISO-8601 로컬 시간 문자열 (Z/오프셋 없이).
     * @param endIso 동일 형식.
     */
    suspend fun calendarView(startIso: String, endIso: String): Result<List<OutlookEventDto>> =
        withBearer { token ->
            val response: GraphListResponse<OutlookEventDto> = client
                .get("$BASE/me/calendarview") {
                    bearer(token)
                    header("Prefer", """outlook.timezone="$timeZone"""")
                    parameter("startDateTime", startIso)
                    parameter("endDateTime", endIso)
                    parameter(
                        "\$select",
                        "id,subject,start,end,isAllDay,seriesMasterId,type,bodyPreview,location",
                    )
                    parameter("\$top", 200)
                }
                .body()
            response.value
        }

    /** `/me/events` POST — 새 이벤트 생성. 반환값은 Graph 가 생성한 이벤트 (id 포함). */
    suspend fun createEvent(payload: OutlookEventWrite): Result<OutlookEventDto> =
        withBearer { token ->
            val response = client.post("$BASE/me/events") {
                bearer(token)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (!response.status.isSuccess()) error("createEvent HTTP ${response.status.value}")
            response.body<OutlookEventDto>()
        }

    /** `/me/events/{id}` PATCH — 부분 업데이트. 필요한 필드만 [payload] 에 담아 넘김. */
    suspend fun updateEvent(id: String, payload: OutlookEventWrite): Result<OutlookEventDto> =
        withBearer { token ->
            val response = client.patch("$BASE/me/events/$id") {
                bearer(token)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (!response.status.isSuccess()) error("updateEvent HTTP ${response.status.value}")
            response.body<OutlookEventDto>()
        }

    /**
     * `/me/events/{id}` DELETE. 204 · 404 (이미 지워짐) 를 성공으로 취급. 반복 시리즈 인스턴스
     * id 를 넘기면 그 발생만 삭제된다 (전체 시리즈 삭제는 seriesMasterId 로 호출).
     */
    suspend fun deleteEvent(id: String): Result<Unit> =
        withBearer { token ->
            val response = client.delete("$BASE/me/events/$id") { bearer(token) }
            val ok = response.status.value == HttpStatusCode.NoContent.value ||
                response.status.value == HttpStatusCode.NotFound.value
            if (!ok) error("deleteEvent HTTP ${response.status.value}")
        }

    // ────────── internals ──────────

    private suspend inline fun <T> withBearer(crossinline block: suspend (String) -> T): Result<T> =
        runCatching {
            val token = auth.accessToken() ?: error("not authenticated (accessToken null)")
            block(token)
        }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    companion object {
        private const val BASE = "https://graph.microsoft.com/v1.0"

        private fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
    }
}

// ────────── DTOs ──────────

/** Graph list response wrapper. `@odata.nextLink` 은 무시 (한 페이지 200개면 충분). */
@Serializable
data class GraphListResponse<T>(val value: List<T>)

/**
 * Graph event 응답 축약. 프로젝트 내부 모델 (`ScheduleDraft` · `Schedule`) 로 변환하는 매퍼는
 * 상위 레이어에서 담당.
 *
 * [type] 값 예시: `singleInstance`, `occurrence`, `exception`, `seriesMaster`.
 * 반복 시리즈의 개별 발생은 `occurrence`, 시리즈 자체는 `seriesMaster` (calendarView 는 발생만 확장).
 */
@Serializable
data class OutlookEventDto(
    val id: String,
    val subject: String? = null,
    val start: OutlookDateTime,
    val end: OutlookDateTime,
    val isAllDay: Boolean = false,
    val type: String? = null,
    val seriesMasterId: String? = null,
    val bodyPreview: String? = null,
    val location: OutlookLocation? = null,
)

@Serializable
data class OutlookDateTime(
    /** ISO-8601 local time (예: `2026-09-10T09:00:00.0000000`). Graph 는 로컬 표기. */
    val dateTime: String,
    /** IANA TZ (예: `Asia/Seoul`) 또는 Windows TZ. `Prefer` 헤더로 원하는 TZ 요구 가능. */
    val timeZone: String,
)

@Serializable
data class OutlookLocation(
    val displayName: String? = null,
)

/**
 * 생성 · 수정 시 body. Graph 는 optional 필드를 아예 안 보내면 유지, `null` 로 보내면 삭제 처리.
 * [end] 를 null 로 넘기지 말 것 (`Bad Request`). All-day 이벤트는 [isAllDay]=true + 시간 00:00.
 */
@Serializable
data class OutlookEventWrite(
    val subject: String,
    val start: OutlookDateTime,
    val end: OutlookDateTime,
    val isAllDay: Boolean = false,
    val body: OutlookBodyWrite? = null,
    val location: OutlookLocation? = null,
)

@Serializable
data class OutlookBodyWrite(
    val contentType: String = "text",
    val content: String,
)
