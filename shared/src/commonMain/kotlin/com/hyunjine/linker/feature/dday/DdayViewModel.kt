package com.hyunjine.linker.feature.dday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.CouplesRepository
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.UsersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 디데이 화면 ViewModel (#329).
 *
 * 로드 순서:
 *  1. 내 couple id · CoupleFull 조회 → anchor 읽음
 *  2. myProfile · partnerProfile 병렬 조회 → 두 사진 · 이니셜용 이름 확보
 *  3. anchor 가 있으면 Filled, 없으면 Empty
 *
 * anchor 저장([saveAnchor])은 optimistic — 저장 요청 중에도 UI 는 새 anchor 로 즉시 갱신.
 * 서버 저장 실패 시 로그만 남기고 다음 refresh 때 재정렬 (에러 UI 는 후속 스코프).
 */
class DdayViewModel : ViewModel() {

    private val _state = MutableStateFlow<DdayUiState>(DdayUiState.Loading)
    val state: StateFlow<DdayUiState> = _state.asStateFlow()

    /** 화면 진입 시 · 편집 후 재조회 시 호출. */
    @OptIn(ExperimentalTime::class)
    fun refresh() {
        viewModelScope.launch {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val coupleId = runCatching { CouplesRepository.myCoupleIdOrNull() }.getOrNull()
            val couple = coupleId?.let {
                runCatching { CouplesRepository.getCoupleById(it) }.getOrNull()
            }
            val mine = runCatching { UsersRepository.myProfile() }.getOrNull()
            val partner = runCatching { UsersRepository.partnerProfile() }.getOrNull()
            val anchor = couple?.ddayAnchorDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

            _state.value = if (anchor != null) {
                DdayUiState.Filled(
                    anchor = anchor,
                    today = today,
                    myImageUrl = mine?.profileImageUrl,
                    myName = mine?.nickname.orEmpty(),
                    partnerImageUrl = partner?.profileImageUrl,
                    partnerName = partner?.nickname.orEmpty(),
                )
            } else {
                DdayUiState.Empty
            }
        }
    }

    /**
     * anchor 저장 (신규 · 편집 공통). Optimistic — UI 는 즉시 Filled 로 전환.
     *
     * 실패 시 (컬럼 없음 · RLS · 네트워크) 이전 state 로 revert + 로그. 이전엔 성공 여부와 무관하게
     * refresh 를 호출해서 실패해도 화면이 조용히 empty 로 돌아가 원인을 파악하기 어려웠음.
     * 이제 성공 시에만 refresh 로 서버값 정렬, 실패 시엔 revert 만.
     */
    @OptIn(ExperimentalTime::class)
    fun saveAnchor(newAnchor: LocalDate) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val previous = _state.value
        val optimistic = when (previous) {
            is DdayUiState.Filled -> previous.copy(anchor = newAnchor, today = today)
            else -> DdayUiState.Filled(
                anchor = newAnchor, today = today,
                myImageUrl = null, myName = "",
                partnerImageUrl = null, partnerName = "",
            )
        }
        _state.value = optimistic
        viewModelScope.launch {
            runCatching { CouplesRepository.updateDdayAnchor(newAnchor) }
                .onSuccess {
                    // milestone 을 공동 캘린더에 종일 일정으로 자동 반영 (#329). anchor 가 바뀔 때마다
                    // 이전 milestone 전량 삭제 → 새 리스트로 교체. 실패해도 anchor 저장은 성공했으니
                    // 로그만 남기고 UI 는 유지.
                    val milestones = buildMilestones(newAnchor)
                    runCatching { SchedulesRepository.replaceDdayMilestones(milestones) }
                        .onFailure { println("[Dday] milestone 캘린더 반영 실패: $it") }
                    // 저장 성공 후 프로필 · 오늘 재조회로 정렬 (empty→filled 최초 진입 시 사진 · 이름 채움).
                    refresh()
                }
                .onFailure { err ->
                    println("[Dday] anchor 저장 실패, 이전 상태로 revert: $err")
                    _state.value = previous
                }
        }
    }
}

sealed interface DdayUiState {
    data object Loading : DdayUiState
    data object Empty : DdayUiState
    data class Filled(
        val anchor: LocalDate,
        val today: LocalDate,
        val myImageUrl: String?,
        val myName: String,
        val partnerImageUrl: String?,
        val partnerName: String,
    ) : DdayUiState
}
