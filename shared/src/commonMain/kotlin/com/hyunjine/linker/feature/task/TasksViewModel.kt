package com.hyunjine.linker.feature.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.SupabaseProvider
import com.hyunjine.linker.feature.main.resolveOwnerForViewer
import com.hyunjine.linker.platform.refreshTodayWidget
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 할 일 내역 화면 (#304) 상태 · 로딩 담당.
 *
 * - "남은 일" = 오늘까지 마감인 미완료 할 일 ([SchedulesRepository.listOpenTasks] — 위젯과 같은 기준).
 *   반복 할 일은 미래 인스턴스가 미리 materialize 돼 있어 상한을 두지 않으면 리스트가 넘친다.
 * - "끝낸 일" = 완료된 할 일 전체 ([SchedulesRepository.listDoneTasks]).
 * - 뷰어 관점 owner 가 `me` · `us` 인 것만 노출. 상대방 할 일은 제외.
 *
 * 두 탭 데이터를 한 번에 받아 두고 탭 전환 · 정렬은 로컬에서만 처리 (재조회 없음).
 */
class TasksViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    init { load() }

    /**
     * 서버에서 두 탭 데이터를 다시 받는다.
     *
     * @param silent true 면 스피너 없이 백그라운드 갱신 (편집 후 복귀 등). 실패해도 기존 리스트 유지.
     */
    fun load(silent: Boolean = false) {
        if (!silent) _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { fetchTasks() }
                .onSuccess { tasks ->
                    _uiState.update { it.copy(loading = false, error = null, tasks = tasks) }
                }
                .onFailure { t ->
                    println("[Tasks] fetch 실패: $t")
                    if (!silent) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                error = t.message?.takeIf { m -> m.isNotBlank() } ?: "네트워크 오류",
                            )
                        }
                    }
                }
        }
    }

    /** @param tab 선택할 탭. */
    fun selectTab(tab: TaskTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    /** @param sort 적용할 정렬 기준. */
    fun selectSort(sort: TaskSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    /**
     * 체크박스 토글. 옵티미스틱으로 즉시 반대 탭으로 옮기고, 실패 시 원복.
     *
     * @param id 토글할 할 일 id.
     * @param onChanged 서버 반영 성공 시 호출 — 상위에서 캘린더 재fetch 트리거용.
     */
    fun toggleDone(id: String, onChanged: () -> Unit) {
        val before = _uiState.value.tasks.firstOrNull { it.id == id } ?: return
        val done = !before.isDone
        _uiState.update { s ->
            s.copy(tasks = s.tasks.map { if (it.id == id) it.copy(isDone = done) else it })
        }
        viewModelScope.launch {
            runCatching { SchedulesRepository.setTaskDone(id, done) }
                .onSuccess {
                    refreshTodayWidget()
                    onChanged()
                }
                .onFailure { t ->
                    println("[Tasks] setTaskDone 실패: $t")
                    _uiState.update { s ->
                        s.copy(tasks = s.tasks.map { if (it.id == id) before else it })
                    }
                }
        }
    }

    private suspend fun fetchTasks(): List<TaskItem> = coroutineScope {
        val open = async { SchedulesRepository.listOpenTasks(today()) }
        val done = async { SchedulesRepository.listDoneTasks() }
        val viewerId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        (open.await() + done.await())
            .filter { resolveOwnerForViewer(it.ownerKind, it.createdBy, viewerId) != "partner" }
            .map { TaskItem(id = it.id, title = it.title, date = LocalDate.parse(it.startDate), isDone = it.isDone) }
    }
}

/** 상단 세그먼트 탭. */
enum class TaskTab(val label: String) {
    Open("남은 일"),
    Done("끝낸 일"),
}

/** 정렬 메뉴 항목. 기준 날짜는 할 일의 날짜 (start_date). */
enum class TaskSort(val label: String) {
    Newest("최신순"),
    Oldest("과거순"),
}

/**
 * 리스트 한 줄에 필요한 값.
 *
 * @param id schedules.id — 편집 진입 · 토글에 사용.
 * @param title 할 일 제목.
 * @param date 할 일 날짜 (start_date).
 * @param isDone 완료 여부. 어느 탭에 속할지 결정.
 */
data class TaskItem(
    val id: String,
    val title: String,
    val date: LocalDate,
    val isDone: Boolean,
)

/**
 * @param loading 최초 로딩 중.
 * @param error 로딩 실패 메시지. null 이면 정상.
 * @param tab 현재 탭.
 * @param sort 현재 정렬.
 * @param tasks 두 탭 전체 할 일. 화면 표시용은 [visibleTasks].
 */
data class TasksUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val tab: TaskTab = TaskTab.Open,
    val sort: TaskSort = TaskSort.Newest,
    val tasks: List<TaskItem> = emptyList(),
) {
    /** 현재 탭 · 정렬이 적용된 리스트. 같은 날짜끼리는 제목순으로 고정해 순서가 튀지 않게. */
    val visibleTasks: List<TaskItem>
        get() {
            val inTab = tasks.filter { it.isDone == (tab == TaskTab.Done) }
            val byTitle = inTab.sortedBy { it.title }
            return when (sort) {
                TaskSort.Newest -> byTitle.sortedByDescending { it.date }
                TaskSort.Oldest -> byTitle.sortedBy { it.date }
            }
        }
}

@kotlin.OptIn(kotlin.time.ExperimentalTime::class)
internal fun today(): LocalDate =
    kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
