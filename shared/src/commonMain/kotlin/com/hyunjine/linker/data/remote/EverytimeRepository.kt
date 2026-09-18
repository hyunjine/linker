package com.hyunjine.linker.data.remote

import com.hyunjine.linker.feature.everytime.EverytimeTimetable
import com.hyunjine.linker.feature.everytime.Lecture
import com.hyunjine.linker.feature.everytime.SemesterRef
import com.hyunjine.linker.feature.everytime.TimeSlot
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * 에브리타임 (`api.everytime.kr`) 시간표 조회 클라이언트 (#306).
 *
 * 공식 API 가 아니라 웹 프론트 (`/@identifier` 페이지) 가 사용하는 XHR 엔드포인트를 그대로 호출한다.
 * 인증은 필요 없으며, 결과는 `application/xml`.
 *
 * 엔드포인트:
 *  - `POST https://api.everytime.kr/find/timetable/table/friend`
 *  - body: `application/x-www-form-urlencoded`
 *    - `identifier=<url @-뒤 문자열>`
 *    - `friendInfo=true` (프론트가 초기 로드 시 그대로 넘김; user/primaryTables 를 함께 받기 위함)
 *
 * 주요 실패 케이스:
 *  - `<response>-1</response>` : identifier 가 없음 (혹은 폐기)
 *  - `<response>-2</response>` : 친구만 볼 수 있는 시간표 — 로그인 없이 접근 불가
 */
object EverytimeRepository {

    private const val BASE = "https://api.everytime.kr"
    private const val ENDPOINT = "$BASE/find/timetable/table/friend"

    private val client: HttpClient by lazy { HttpClient() }
    private val xml: XML by lazy {
        XML {
            defaultPolicy {
                // 응답에는 우리가 쓰지 않는 element (예: `<internal value=".."/>`) 가 여럿 있어 무시 필요.
                ignoreUnknownChildren()
            }
        }
    }

    /**
     * 주어진 identifier 의 대표 시간표 (기본 학기) 를 조회.
     * @throws EverytimeException 응답이 실패 코드거나 파싱 불가일 때.
     */
    suspend fun fetchTimetable(identifier: String): EverytimeTimetable {
        val response: HttpResponse = client.submitForm(
            url = ENDPOINT,
            formParameters = Parameters.build {
                append("identifier", identifier)
                append("friendInfo", "true")
            },
        ) {
            // Origin 을 붙여야 CORS 우회는 물론이고 서버가 정상 응답 (일부 경로에서 origin 검사).
            header(HttpHeaders.Origin, "https://everytime.kr")
            header(HttpHeaders.Referrer, "https://everytime.kr/@$identifier")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
        if (!response.status.isSuccess()) {
            throw EverytimeException("HTTP ${response.status.value}")
        }
        val body = response.bodyAsText()
        return parseBody(identifier, body)
    }

    internal fun parseBody(identifier: String, body: String): EverytimeTimetable {
        // `<response>-1</response>` 처럼 body 가 짧고 자식 없는 정수만 있는 형태 = 실패 코드.
        val trimmed = body.trim()
        FailureCodeRegex.find(trimmed)?.let { m ->
            val code = m.groupValues[1]
            throw EverytimeException(
                when (code) {
                    "-1" -> "존재하지 않는 시간표"
                    "-2" -> "친구만 볼 수 있는 시간표"
                    else -> "에브리타임 응답 오류 ($code)"
                }
            )
        }
        val dto = runCatching { xml.decodeFromString(ResponseXml.serializer(), body) }
            .getOrElse { throw EverytimeException("응답 파싱 실패: ${it.message}") }

        val lectures = dto.table?.subjects.orEmpty().map { s ->
            Lecture(
                id = s.id.orEmpty(),
                name = s.name?.value.orEmpty(),
                professor = s.professor?.value.orEmpty(),
                credit = s.credit?.value?.toIntOrNull() ?: 0,
                defaultPlace = s.place?.value.orEmpty(),
                slots = s.time?.slots.orEmpty().mapNotNull { t ->
                    val day = t.day ?: return@mapNotNull null
                    val start = t.starttime ?: return@mapNotNull null
                    val end = t.endtime ?: return@mapNotNull null
                    TimeSlot(day = day, startSlot = start, endSlot = end, place = t.place.orEmpty())
                },
            )
        }
        val semesters = dto.primaryTables?.items.orEmpty().mapNotNull { p ->
            val id = p.identifier ?: return@mapNotNull null
            SemesterRef(year = p.year ?: 0, semester = p.semester?.toIntOrNull() ?: 0, identifier = id)
        }
        return EverytimeTimetable(
            ownerName = dto.user?.name.orEmpty(),
            year = dto.table?.year,
            semester = dto.table?.semester?.toIntOrNull(),
            identifier = dto.table?.identifier ?: identifier,
            lectures = lectures,
            availableSemesters = semesters,
        )
    }

    private val FailureCodeRegex = Regex("""<response>\s*(-?\d+)\s*</response>""")

    // Everytime 프론트가 보내는 UA 와 유사한 흔한 데스크톱 브라우저 UA — 서버가 mobile UA 를
    // 걸러내는 케이스를 방어. 값 자체는 secrets 도 아니고 사용자 특정도 안 됨.
    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // ────────── XML DTO ──────────

    @Serializable
    @XmlSerialName("response")
    private data class ResponseXml(
        @XmlElement(true) val table: TableXml? = null,
        @XmlElement(true) val user: UserXml? = null,
        @XmlElement(true) val primaryTables: PrimaryTablesXml? = null,
    )

    @Serializable
    @XmlSerialName("table")
    private data class TableXml(
        val year: Int? = null,
        val semester: String? = null,
        val status: Int? = null,
        val identifier: String? = null,
        // xmlutil 기본 정책은 List 를 flatten — 각 아이템은 SubjectXml 에 붙은 @XmlSerialName("subject") 로 매핑.
        val subjects: List<SubjectXml> = emptyList(),
    )

    @Serializable
    @XmlSerialName("subject")
    private data class SubjectXml(
        val id: String? = null,
        @XmlElement(true) val name: NameEl? = null,
        @XmlElement(true) val professor: ProfessorEl? = null,
        @XmlElement(true) val time: TimeContainer? = null,
        @XmlElement(true) val place: PlaceEl? = null,
        @XmlElement(true) val credit: CreditEl? = null,
        @XmlElement(true) val closed: ClosedEl? = null,
    )

    @Serializable @XmlSerialName("name")      private data class NameEl(val value: String? = null)
    @Serializable @XmlSerialName("professor") private data class ProfessorEl(val value: String? = null)
    @Serializable @XmlSerialName("place")     private data class PlaceEl(val value: String? = null)
    @Serializable @XmlSerialName("credit")    private data class CreditEl(val value: String? = null)
    @Serializable @XmlSerialName("closed")    private data class ClosedEl(val value: String? = null)

    @Serializable
    @XmlSerialName("time")
    private data class TimeContainer(
        val value: String? = null,
        val slots: List<TimeDataXml> = emptyList(),
    )

    @Serializable
    @XmlSerialName("data")
    private data class TimeDataXml(
        val day: Int? = null,
        val starttime: Int? = null,
        val endtime: Int? = null,
        val place: String? = null,
    )

    @Serializable
    @XmlSerialName("user")
    private data class UserXml(val name: String? = null)

    @Serializable
    @XmlSerialName("primaryTables")
    private data class PrimaryTablesXml(
        val items: List<PrimaryTableXml> = emptyList(),
    )

    @Serializable
    @XmlSerialName("primaryTable")
    private data class PrimaryTableXml(
        val year: Int? = null,
        val semester: String? = null,
        val identifier: String? = null,
    )
}

/** 에브리타임 API 가 리턴한 실패 코드나 파싱 실패를 감싸는 단일 예외. UI 는 [message] 를 그대로 노출. */
class EverytimeException(message: String) : RuntimeException(message)
