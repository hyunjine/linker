package com.hyunjine.linker.feature.everytime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.EverytimeException
import com.hyunjine.linker.data.remote.EverytimeRepository
import com.hyunjine.linker.data.remote.UsersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 탭 축 — 본인 · 상대방 두 시간표를 한 화면에서 스위칭 (#306 · 확장). */
enum class TimetableOwner { Me, Partner }

/**
 * 본인/파트너 에브리타임 시간표를 세그먼트 탭으로 스위칭하며 렌더 (#306).
 *
 * 진입 시퀀스:
 *  1. 본인 · 파트너 프로필을 병렬 조회 → 각각의 identifier · nickname 확보.
 *  2. `activeOwner` 기본값 = Partner (기존 진입 흐름 유지).
 *  3. 해당 owner 의 identifier 가 있으면 즉시 fetch, 없으면 empty state.
 *  4. 사용자가 탭을 바꾸면 [selectTab] → 해당 owner tab payload 가 비어 있으면 fetch.
 *  5. 사용자가 empty state 의 "URL 추가하기" 버튼을 눌러 자신의 URL 을 저장하면
 *     [saveMyIdentifier] → DB update → 프로필 재조회 → 본인 tab 재fetch.
 *
 * 학기 목록 · 학기 이동은 각 tab 별로 독립. 응답의 `availableSemesters` 를 소스 오브 트루스로.
 */
class EverytimeTimetableViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EverytimeUiState())
    val uiState: StateFlow<EverytimeUiState> = _uiState.asStateFlow()

    init { loadProfiles() }

    /** 본인 · 파트너 프로필을 한 번에 조회. 실패해도 다른 쪽은 계속 시도. */
    private fun loadProfiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingProfiles = true)
            val me = runCatching { UsersRepository.myProfile() }
                .onFailure { println("[Everytime] 본인 프로필 조회 실패: $it") }
                .getOrNull()
            val partner = runCatching { UsersRepository.partnerProfile() }
                .onFailure { println("[Everytime] 파트너 프로필 조회 실패: $it") }
                .getOrNull()
            _uiState.value = _uiState.value.copy(
                loadingProfiles = false,
                myNickname = me?.nickname.orEmpty(),
                partnerNickname = partner?.nickname.orEmpty(),
                myIdentifier = me?.everytimeIdentifier,
                partnerIdentifier = partner?.everytimeIdentifier,
            )
            // 프로필 로드 완료 후 활성 탭의 시간표를 자동 fetch.
            fetchActiveIfNeeded()
        }
    }

    fun selectTab(owner: TimetableOwner) {
        val current = _uiState.value.activeOwner
        if (current == owner) return
        _uiState.value = _uiState.value.copy(activeOwner = owner)
        fetchActiveIfNeeded()
    }

    fun selectSemester(ref: SemesterRef) {
        val active = _uiState.value.activeOwner
        val currentId = _uiState.value.tabOf(active).timetable?.identifier
        if (currentId == ref.identifier) return
        fetch(active, ref.identifier)
    }

    /**
     * 사용자가 URL 시트에서 붙여넣은 raw 문자열을 파싱해 본인 identifier 로 저장.
     * 빈 문자열이면 identifier 를 NULL 로 되돌려 등록 취소.
     * 유효하지 않은 값이면 에러만 노출 (시트는 상위에서 dismiss).
     */
    fun saveMyIdentifier(rawUrl: String) {
        val trimmed = rawUrl.trim()
        val parsed = if (trimmed.isEmpty()) null else EverytimeUrl.parseIdentifier(trimmed)
        // 시트에서 이미 canConfirm 로 검증했지만 방어적으로 재검증.
        if (trimmed.isNotEmpty() && parsed == null) {
            _uiState.value = _uiState.value.copy(urlSaveError = "올바른 에브리타임 URL 이 아니에요.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(savingUrl = true, urlSaveError = null)
            runCatching { UsersRepository.updateEverytimeIdentifier(parsed) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        savingUrl = false,
                        myIdentifier = parsed,
                        // 새 identifier 로 다시 로드하도록 본인 tab 페이로드 리셋.
                        me = TabPayload(),
                    )
                    if (parsed != null) fetch(TimetableOwner.Me, parsed)
                }
                .onFailure { e ->
                    println("[Everytime] URL 저장 실패: $e")
                    _uiState.value = _uiState.value.copy(
                        savingUrl = false,
                        urlSaveError = "URL 을 저장하지 못했어요. 잠시 후 다시 시도해주세요.",
                    )
                }
        }
    }

    fun clearUrlSaveError() {
        if (_uiState.value.urlSaveError != null) {
            _uiState.value = _uiState.value.copy(urlSaveError = null)
        }
    }

    /** 활성 탭에 identifier 가 있는데 아직 시간표가 없으면 fetch. */
    private fun fetchActiveIfNeeded() {
        val state = _uiState.value
        val owner = state.activeOwner
        val id = state.identifierOf(owner) ?: return
        val payload = state.tabOf(owner)
        if (payload.timetable == null && !payload.loading) {
            fetch(owner, id)
        }
    }

    private fun fetch(owner: TimetableOwner, identifier: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.updateTab(owner) {
                it.copy(loading = true, error = null)
            }
            runCatching { EverytimeRepository.fetchTimetable(identifier) }
                .onSuccess { data ->
                    _uiState.value = _uiState.value.updateTab(owner) { prev ->
                        prev.copy(
                            loading = false,
                            timetable = data,
                            semesters = data.availableSemesters.takeIf { it.isNotEmpty() }
                                ?: prev.semesters,
                        )
                    }
                }
                .onFailure { e ->
                    println("[Everytime] fetch 실패 (owner=$owner): $e")
                    _uiState.value = _uiState.value.updateTab(owner) {
                        it.copy(
                            loading = false,
                            error = (e as? EverytimeException)?.message
                                ?: "시간표를 불러오지 못했어요.",
                        )
                    }
                }
        }
    }
}

/** 탭별 시간표 · 로딩 · 에러 · 학기 목록. tab 마다 독립적으로 캐시. */
data class TabPayload(
    val loading: Boolean = false,
    val timetable: EverytimeTimetable? = null,
    val semesters: List<SemesterRef> = emptyList(),
    val error: String? = null,
)

/**
 * 두 탭 (본인 · 파트너) 상태를 하나로 묶은 UI state.
 * 활성 탭 페이로드에 접근할 땐 [tabOf] / [updateTab] 헬퍼를 쓴다.
 */
data class EverytimeUiState(
    val activeOwner: TimetableOwner = TimetableOwner.Partner,
    val loadingProfiles: Boolean = true,
    val myNickname: String = "",
    val partnerNickname: String = "",
    val myIdentifier: String? = null,
    val partnerIdentifier: String? = null,
    val me: TabPayload = TabPayload(),
    val partner: TabPayload = TabPayload(),
    val savingUrl: Boolean = false,
    val urlSaveError: String? = null,
) {
    fun tabOf(owner: TimetableOwner): TabPayload = when (owner) {
        TimetableOwner.Me -> me
        TimetableOwner.Partner -> partner
    }

    fun identifierOf(owner: TimetableOwner): String? = when (owner) {
        TimetableOwner.Me -> myIdentifier
        TimetableOwner.Partner -> partnerIdentifier
    }

    fun nicknameOf(owner: TimetableOwner): String = when (owner) {
        TimetableOwner.Me -> myNickname
        TimetableOwner.Partner -> partnerNickname
    }

    /** 특정 owner tab 페이로드만 변경. */
    fun updateTab(owner: TimetableOwner, block: (TabPayload) -> TabPayload): EverytimeUiState =
        when (owner) {
            TimetableOwner.Me -> copy(me = block(me))
            TimetableOwner.Partner -> copy(partner = block(partner))
        }
}
