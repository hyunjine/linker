package com.hyunjine.linker.feature.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.ReleasesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 릴리즈 노트 화면 상태 · 로딩 담당. 진입 시 [load] 로 원격 fetch — 캐시 없이 매번 GitHub API
 * 재호출 (issue #255 명세).
 *
 * draft · prerelease 는 사용자에게 노출하지 않도록 필터.
 */
class ReleaseNotesViewModel : ViewModel() {

    private val repo = ReleasesRepository()

    private val _uiState = MutableStateFlow(ReleaseNotesUiState())
    val uiState: StateFlow<ReleaseNotesUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repo.fetchAll() }
                .onSuccess { list ->
                    _uiState.value = ReleaseNotesUiState(
                        loading = false,
                        releases = list.filterNot { it.draft || it.prerelease },
                    )
                }
                .onFailure { t ->
                    println("[ReleaseNotes] fetch 실패: $t")
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = t.message?.takeIf { it.isNotBlank() } ?: "네트워크 오류",
                    )
                }
        }
    }
}

data class ReleaseNotesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val releases: List<ReleasesRepository.GithubRelease> = emptyList(),
)
