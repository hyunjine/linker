package com.hyunjine.linker.adminweb.console

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hyunjine.linker.adminweb.data.Account
import com.hyunjine.linker.adminweb.data.AdminAccountsRepository
import com.hyunjine.linker.adminweb.data.remote.AdminSupabase
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 발송 콘솔 화면의 UI 상태 · 액션 홀더.
 *
 * ViewModel 을 도입하지 않은 이유:
 * - `:adminWeb` 은 단일 페이지 앱이라 라이프사이클/프로세스 재생성이 없음.
 * - androidx.lifecycle 의존을 wasmJs 로 끌어오는 비용이 상태 관리 이득보다 큼.
 *
 * 대신 이 클래스가 Compose `mutableStateOf` 기반 프로퍼티들을 노출하고, 코루틴 스코프를 스스로
 * 소유하며, 스크린 컴포저블은 `remember { ConsoleState(...) }` 로 이 인스턴스를 붙잡는다.
 * — docs/261-admin-push-console.md §3.3, §3.4, §7.
 *
 * @param scope 백엔드 호출용 코루틴 스코프. 기본값은 앱 생명주기 동안 유지되는 SupervisorJob.
 * @param fetchAccounts 계정 조회 함수. 테스트 편의를 위한 훅 (기본값은 [AdminAccountsRepository.fetchAll]).
 * @param sendAnnouncement 실제 Edge Function 호출. 테스트에서 대체 가능.
 */
class ConsoleState(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val fetchAccounts: suspend () -> List<Account> = { AdminAccountsRepository.fetchAll() },
    private val sendAnnouncement: suspend (SendRequest) -> SendOutcome = { defaultSendAnnouncement(it) },
) {

    /** 계정 로딩 상태. `Loading` → `Loaded` | `Failed`. */
    var loadState: LoadState by mutableStateOf(LoadState.Loading)
        private set

    /** 최신 계정 목록 (닉네임 오름차순). [refresh] 성공 시 채워짐. */
    var accounts: List<Account> by mutableStateOf(emptyList())
        private set

    /** 닉네임 검색어 (클라이언트 필터). */
    var search: String by mutableStateOf("")

    /** 선택된 계정 id 집합. 순서는 무의미. */
    var selectedIds: Set<String> by mutableStateOf(emptySet())
        private set

    /** 메시지 제목 원본. `title.length` 로 카운트, 상위 UI 에서 [TitleMaxLength] 초과 여부 판정. */
    var title: String by mutableStateOf("")

    /** 메시지 본문 원본. */
    var body: String by mutableStateOf("")

    /** 발송 중 여부. `true` 면 pill 이 로딩 스피너를 그리고 재발송을 차단. */
    var sending: Boolean by mutableStateOf(false)
        private set

    /** 스낵바로 UI 에 전달할 메시지. 한 번 소비되면 [consumeSnackbar] 로 null 리셋. */
    var snackbar: SnackbarMessage? by mutableStateOf(null)
        private set

    /**
     * 검색어에 매칭되는 계정 뷰. 검색어가 비어 있으면 [accounts] 전체.
     *
     * 대소문자 무시 · 닉네임 substring 매칭. UI 는 이 값을 그대로 렌더한다.
     */
    val filteredAccounts: List<Account>
        get() {
            val q = search.trim()
            if (q.isEmpty()) return accounts
            val lower = q.lowercase()
            return accounts.filter { it.nickname.lowercase().contains(lower) }
        }

    /**
     * "필터된 리스트 기준" 모두 선택 상태.
     *
     * §9 결정 — 검색이 걸린 상태에서 "모두 선택" 은 필터된 결과 전체를 의미. 따라서 필터된
     * 계정이 모두 [selectedIds] 에 담겨 있고 필터된 리스트가 비어있지 않을 때만 true.
     */
    val allFilteredSelected: Boolean
        get() {
            val filtered = filteredAccounts
            if (filtered.isEmpty()) return false
            return filtered.all { it.id in selectedIds }
        }

    /** 발송 버튼 활성 조건. — docs §3.4. */
    val canSend: Boolean
        get() = selectedIds.isNotEmpty() &&
            title.trim().isNotEmpty() &&
            body.trim().isNotEmpty() &&
            !sending &&
            title.length <= TitleMaxLength &&
            body.length <= BodyMaxLength

    /** 계정 목록을 초기 로드하거나 실패 후 재시도. */
    fun refresh() {
        loadState = LoadState.Loading
        scope.launch {
            runCatching { fetchAccounts() }
                .onSuccess { fetched ->
                    accounts = fetched
                    // 최신 목록 기준으로 selectedIds 를 정리 — 사라진 유저의 id 는 제거.
                    val validIds = fetched.map { it.id }.toSet()
                    selectedIds = selectedIds.intersect(validIds)
                    loadState = LoadState.Loaded
                }
                .onFailure { throwable ->
                    println("[Console] 계정 조회 실패: $throwable")
                    loadState = LoadState.Failed(throwable.message ?: "계정 목록을 불러오지 못했어요.")
                }
        }
    }

    /** 특정 계정 [id] 의 선택 여부를 토글. */
    fun toggle(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    /**
     * "모두 선택" 토글. 현재 [allFilteredSelected] 이면 필터된 계정들을 [selectedIds] 에서 제거,
     * 아니면 필터된 계정 전부를 추가.
     *
     * §9 열린 결정 — "필터된 결과 전체" 를 대상으로 함.
     */
    fun toggleAllFiltered() {
        val filtered = filteredAccounts
        if (filtered.isEmpty()) return
        val filteredIds = filtered.map { it.id }.toSet()
        selectedIds = if (allFilteredSelected) {
            selectedIds - filteredIds
        } else {
            selectedIds + filteredIds
        }
    }

    /**
     * 발송 실행. 성공 시 폼 초기화 + 성공 스낵바, 실패 시 실패 스낵바 (폼 유지).
     *
     * 낙관적 갱신은 하지 않는다 (§7) — 응답을 기다렸다가 결과 스낵바를 띄운다.
     */
    fun send() {
        if (!canSend) return
        sending = true
        val payload = SendRequest(
            title = title.trim(),
            body = body.trim(),
            userIds = selectedIds.toList(),
        )
        scope.launch {
            runCatching { sendAnnouncement(payload) }
                .onSuccess { outcome ->
                    snackbar = SnackbarMessage.success(outcome)
                    // 폼 초기화 (§7).
                    title = ""
                    body = ""
                    selectedIds = emptySet()
                }
                .onFailure { throwable ->
                    println("[Console] send-announcement 실패: $throwable")
                    snackbar = SnackbarMessage.error(throwable.message ?: "알 수 없는 오류")
                }
                .also { sending = false }
        }
    }

    /** 스낵바를 UI 가 소비 완료했음을 알린다 (dismiss). */
    fun consumeSnackbar() {
        snackbar = null
    }

    /** 계정 목록 로딩 · 완료 · 실패의 3 상태. */
    sealed interface LoadState {
        data object Loading : LoadState
        data object Loaded : LoadState
        data class Failed(val message: String) : LoadState
    }

    companion object {
        /** 제목 최대 글자 수. 초과 시 요약 카운트가 빨강. — docs §3.4. */
        const val TitleMaxLength: Int = 40

        /** 본문 최대 글자 수. */
        const val BodyMaxLength: Int = 300
    }
}

/** Edge Function 요청 페이로드. — docs §4.2. */
@Serializable
data class SendRequest(
    val title: String,
    val body: String,
    val userIds: List<String>,
)

/** Edge Function 응답 페이로드. — docs §4.2. */
@Serializable
data class SendResponse(
    val sent: Int = 0,
    val failed: Int = 0,
    val errors: List<SendError> = emptyList(),
) {
    @Serializable
    data class SendError(val userId: String, val reason: String)
}

/**
 * 발송 결과 도메인 모델. UI 는 이 값으로 스낵바 문구를 만든다.
 *
 * @property sent 성공한 발송 건수.
 * @property failed 실패한 발송 건수.
 */
data class SendOutcome(val sent: Int, val failed: Int)

/** 콘솔 스낵바에 노출할 안내 메시지. */
data class SnackbarMessage(val text: String, val kind: Kind) {
    enum class Kind { Success, Error }

    companion object {
        fun success(outcome: SendOutcome): SnackbarMessage {
            val total = outcome.sent + outcome.failed
            val base = "${outcome.sent}/${total} 발송 성공"
            val text = if (outcome.failed > 0) "$base, ${outcome.failed}건 실패" else base
            return SnackbarMessage(text = text, kind = Kind.Success)
        }

        fun error(reason: String): SnackbarMessage =
            SnackbarMessage(text = "발송 실패: $reason", kind = Kind.Error)
    }
}

/**
 * 기본 send-announcement Edge Function 호출.
 *
 * supabase-kt 3.7 은 `Functions.invoke(functionName, body, region, headers)` 타입드 오버로드를
 * 제공하지만, 응답을 typed decode 하려면 ContentNegotiation 을 별도로 설치해야 한다.
 * 이 콘솔은 응답이 소규모라 `bodyAsText` + 수동 [Json.decodeFromString] 으로 처리 —
 * ContentNegotiation 의존 없이 최소한의 표면을 유지한다.
 */
private suspend fun defaultSendAnnouncement(payload: SendRequest): SendOutcome {
    val response: HttpResponse = AdminSupabase.client.functions.invoke(
        function = "send-announcement",
        body = payload,
    )
    val raw = response.bodyAsText()
    val decoded = ResponseJson.decodeFromString(SendResponse.serializer(), raw)
    return SendOutcome(sent = decoded.sent, failed = decoded.failed)
}

/**
 * 응답 파싱용 Json. Edge Function 은 후속 이슈에서 필드를 늘릴 수 있으므로 관대하게 파싱.
 */
private val ResponseJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
