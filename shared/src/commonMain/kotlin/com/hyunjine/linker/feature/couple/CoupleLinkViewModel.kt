package com.hyunjine.linker.feature.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.CouplesRepository
import com.hyunjine.linker.data.remote.UsersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * CoupleLinkScreen — 진입 시 현재 파트너 조인 상태를 확인한다.
 *
 * - [CoupleLinkUiState.Loading]: 서버 응답 대기.
 * - [CoupleLinkUiState.NotPaired]: 파트너 미조인. 두 개 옵션 (내 초대코드 · 상대 코드 입력) 노출.
 * - [CoupleLinkUiState.Paired]: 파트너와 연결됨. 파트너 프로필 카드 + 연결 해제 UI.
 *
 * 파트너 조인 여부는 `couples.linked_at` non-null 로 판정.
 * 아예 커플 자체가 없는 유저 (미가입) 는 NotPaired 로 취급 — 옵션 진입 시 초대코드 화면이
 * `create_my_couple` 로 자동 생성.
 */
class CoupleLinkViewModel : ViewModel() {

    private val _state = MutableStateFlow<CoupleLinkUiState>(CoupleLinkUiState.Loading)
    val state: StateFlow<CoupleLinkUiState> = _state.asStateFlow()

    /**
     * 파트너 조인 상태 · 프로필을 다시 조회한다. `CoupleLinkRoute` 의 `LifecycleResumeEffect`
     * 가 최초 진입 · RESUMED 재진입마다 호출해 stale UI 를 방어한다 (init 대신 사용).
     */
    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val id = CouplesRepository.myCoupleIdOrNull() ?: return@runCatching null
                val full = CouplesRepository.getCoupleById(id) ?: return@runCatching null
                // linked_at 이 null 이면 아직 혼자 있는 solo couple → 파트너 프로필 fetch 스킵.
                val partner = if (full.linkedAt != null) UsersRepository.partnerProfile() else null
                // 공동 색 picker 초기값 = 내 프로필의 us_calendar_color (#245).
                val mine = UsersRepository.myProfile()
                Triple(full, partner, mine?.usCalendarColor)
            }.onSuccess { triple ->
                val (full, partner, usColor) = triple ?: Triple(null, null, null)
                _state.value = if (full?.linkedAt != null) {
                    CoupleLinkUiState.Paired(partner = partner, usCalendarColor = usColor)
                } else {
                    CoupleLinkUiState.NotPaired
                }
            }.onFailure {
                println("[Couple] link status 조회 실패: $it")
                _state.value = CoupleLinkUiState.NotPaired
            }
        }
    }

    /**
     * 공동(Us) 캘린더 색 저장 (#245 · #323). Optimistic update — 사용자가 스와치를 탭하면 UI 는
     * 즉시 새 값으로 갱신되고, 백그라운드에서 users.us_calendar_color 를 patch.
     *
     * 실패 시 (컬럼 없음 · RLS · 네트워크 등) UI 를 이전 값으로 되돌리고 [saveError] 를 세팅해
     * "저장 안 됐음" 을 사용자가 인지할 수 있게 한다. 이전엔 실패해도 optimistic 값이 화면에
     * 남았다가 다음 refresh 때 조용히 revert 되어 "저장 됐다고 착각" 하는 문제가 있었음.
     */
    fun updateUsColor(newId: String) {
        val current = _state.value
        if (current !is CoupleLinkUiState.Paired) return
        val previousId = current.usCalendarColor
        _state.value = current.copy(usCalendarColor = newId, saveError = null)
        viewModelScope.launch {
            runCatching { UsersRepository.updateUsCalendarColor(newId) }
                .onFailure { err ->
                    println("[Couple] us 색 저장 실패: $err")
                    val now = _state.value
                    if (now is CoupleLinkUiState.Paired) {
                        _state.value = now.copy(
                            usCalendarColor = previousId,
                            saveError = "색상 저장에 실패했어요. 잠시 후 다시 시도해주세요.",
                        )
                    }
                }
        }
    }

    /** UI 가 에러 배너를 닫았을 때 호출. 다음 저장 시도 시엔 자동으로 클리어됨. */
    fun clearSaveError() {
        val current = _state.value
        if (current is CoupleLinkUiState.Paired && current.saveError != null) {
            _state.value = current.copy(saveError = null)
        }
    }

    /**
     * 커플 연결 해제. 서버 RPC 로 새 solo couple 로 옮긴다. 성공하면 상태를 NotPaired 로 갱신하고
     * [onDone] 호출 (상위가 tick 을 올려 Main · Drawer 도 재조회하도록).
     */
    fun unlink(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { CouplesRepository.unlinkCouple() }
                .onSuccess {
                    println("[Couple] unlink 성공. 새 couple=$it")
                    _state.value = CoupleLinkUiState.NotPaired
                    onDone()
                }
                .onFailure { println("[Couple] unlink 실패: $it") }
        }
    }
}

sealed interface CoupleLinkUiState {
    data object Loading : CoupleLinkUiState
    data object NotPaired : CoupleLinkUiState
    /**
     * 파트너 조인 완료. [partner] 는 파트너 `public.users` 프로필 (닉네임·생일·아바타·색).
     * RLS · Realtime · 삭제 rc 등으로 조회 실패하면 null — 이 경우 프로필 카드 자리를 감춘다.
     * [usCalendarColor] 는 내 프로필의 공동 색 preference. NULL 이면 UI 가 CalendarPurple 로 fallback.
     * [saveError] 는 공동 색 저장 실패 시 사용자에게 보여줄 메시지 (#323). null 이면 노출 안 함.
     */
    data class Paired(
        val partner: UsersRepository.Profile?,
        val usCalendarColor: String? = null,
        val saveError: String? = null,
    ) : CoupleLinkUiState
}
