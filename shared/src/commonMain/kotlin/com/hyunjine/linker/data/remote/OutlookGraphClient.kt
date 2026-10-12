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
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Microsoft Graph API 클라이언트 (v1.0 `/me/calendars/{id}/calendarView` · `/me/calendars/{id}/events`).
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
     * 내 캘린더 목록에서 [name] 과 이름이 같은 캘린더 id. 공유받은 팀 캘린더 (TeamAIVN) 도 포함.
     *
     * @param name 캘린더 표시 이름.
     * @return 없으면 null.
     */
    suspend fun findCalendarId(name: String): Result<String?> =
        withBearer { token ->
            val response: GraphListResponse<CalendarDto> = client.get("$BASE/me/calendars") {
                bearer(token)
                parameter("\$select", "id,name")
                parameter("\$top", 100)
            }.body()
            response.value.firstOrNull { it.name.trim() == name }?.id
        }

    /**
     * `/me/calendars/{id}/calendarView` — 지정 캘린더 · date range 내의 모든 (반복 시리즈 인스턴스 포함) 이벤트.
     * Graph 는 series master 하나만 주지 않고 매 발생일마다 별도 인스턴스로 확장해 반환.
     * @param calendarId [findCalendarId] 로 찾은 캘린더 id.
     * @param startIso `2026-09-01T00:00:00` 같은 ISO-8601 로컬 시간 문자열 (Z/오프셋 없이).
     * @param endIso 동일 형식.
     */
    suspend fun calendarView(calendarId: String, startIso: String, endIso: String): Result<List<OutlookEventDto>> =
        withBearer { token ->
            // 회사 캘린더는 몇 달치만 해도 수백 건이라 `@odata.nextLink` 를 끝까지 따라간다 (#242).
            // nextLink 에는 원래 쿼리가 모두 들어 있어 Prefer 헤더만 다시 실으면 된다.
            val events = mutableListOf<OutlookEventDto>()
            var page: GraphListResponse<OutlookEventDto> = client
                .get("$BASE/me/calendars/$calendarId/calendarView") {
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
            events += page.value
            var pages = 1
            while (page.nextLink != null && pages < MaxPages) {
                page = client.get(page.nextLink!!) {
                    bearer(token)
                    header("Prefer", """outlook.timezone="$timeZone"""")
                }.body()
                events += page.value
                pages++
            }
            events
        }

    /**
     * `/me/events/{id}` GET — 단건 조회. 앱에서 미러를 고칠 때 원본 제목 (이름) · 시각을 읽는 용도.
     * start · end 는 [timeZone] 기준 로컬 시각으로 받는다.
     *
     * @param calendarId 일정이 있는 캘린더 id.
     * @param id Graph event id (미러의 external_id).
     */
    suspend fun getEvent(calendarId: String, id: String): Result<OutlookEventDto> =
        withBearer { token ->
            val response = client.get("$BASE/me/calendars/$calendarId/events/$id") {
                bearer(token)
                header("Prefer", """outlook.timezone="$timeZone"""")
                parameter("\$select", "id,subject,start,end,isAllDay,seriesMasterId,type")
            }
            if (!response.status.isSuccess()) error("getEvent HTTP ${response.status.value}")
            response.body<OutlookEventDto>()
        }

    /**
     * `/me/events/{id}` PATCH — [patch] 에 담긴 필드만 바꾼다. 본문 · 장소 · 참석자 등 나머지는
     * 원본 그대로 유지 (null 필드는 직렬화하지 않음).
     *
     * @param calendarId 일정이 있는 캘린더 id.
     * @param id Graph event id.
     * @param patch 바꿀 필드.
     */
    suspend fun patchEvent(calendarId: String, id: String, patch: OutlookEventPatch): Result<Unit> =
        withBearer { token ->
            val response = client.patch("$BASE/me/calendars/$calendarId/events/$id") {
                bearer(token)
                contentType(ContentType.Application.Json)
                setBody(PatchJson.encodeToString(OutlookEventPatch.serializer(), patch))
            }
            if (!response.status.isSuccess()) error("patchEvent HTTP ${response.status.value}")
        }

    /**
     * `/me/events/{id}` DELETE. 204 · 404 (이미 지워짐) 를 성공으로 취급. 반복 시리즈 인스턴스
     * id 를 넘기면 그 발생만 삭제된다 (전체 시리즈 삭제는 seriesMasterId 로 호출).
     *
     * @param calendarId 일정이 있는 캘린더 id.
     * @param id Graph event id.
     */
    suspend fun deleteEvent(calendarId: String, id: String): Result<Unit> =
        withBearer { token ->
            val response = client.delete("$BASE/me/calendars/$calendarId/events/$id") { bearer(token) }
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

        /** nextLink 무한 루프 방지용 상한 (200건 × 20쪽 = 4,000건). */
        private const val MaxPages = 20

        /** PATCH body 용 — null 필드를 아예 빼서 Graph 가 "삭제" 로 해석하지 않게. */
        private val PatchJson = Json { explicitNulls = false; encodeDefaults = false }

        private fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
    }
}

// ────────── DTOs ──────────

/**
 * Graph list response wrapper.
 *
 * @param value 이번 페이지 항목.
 * @param nextLink 다음 페이지 전체 URL. 마지막 페이지면 null.
 */
@Serializable
data class GraphListResponse<T>(
    val value: List<T>,
    @SerialName("@odata.nextLink") val nextLink: String? = null,
)

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

/**
 * 캘린더 목록 항목.
 *
 * @param id 캘린더 id.
 * @param name 표시 이름.
 */
@Serializable
data class CalendarDto(val id: String, val name: String)

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
 * 부분 수정 body. null 인 필드는 보내지 않아 원본 값이 유지된다.
 *
 * @param subject 새 제목.
 * @param start 새 시작 시각.
 * @param end 새 종료 시각.
 */
@Serializable
data class OutlookEventPatch(
    val subject: String? = null,
    val start: OutlookDateTime? = null,
    val end: OutlookDateTime? = null,
)
