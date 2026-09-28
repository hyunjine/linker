package com.hyunjine.linker.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.Secrets
import com.hyunjine.linker.data.remote.ReleasesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 더보기 화면 (#385) 상태. 현재 버전은 빌드 때 생성된 [Secrets.AppVersion], 최신 버전은 GitHub Releases
 * 의 최신 정식 릴리즈 (draft · prerelease 제외) 태그.
 */
class MoreViewModel : ViewModel() {

    private val repo = ReleasesRepository()

    private val _uiState = MutableStateFlow(MoreUiState(currentVersion = Secrets.AppVersion))
    val uiState: StateFlow<MoreUiState> = _uiState.asStateFlow()

    init { loadLatest() }

    private fun loadLatest() {
        viewModelScope.launch {
            val latest = runCatching { repo.fetchAll() }
                .onFailure { println("[More] 최신 버전 조회 실패: $it") }
                .getOrNull()
                ?.firstOrNull { !it.draft && !it.prerelease }
                ?.tagName
                ?.removePrefix("v")
            _uiState.update {
                it.copy(latest = if (latest == null) LatestVersion.Unavailable else LatestVersion.Loaded(latest))
            }
        }
    }
}

/**
 * @param currentVersion 설치된 앱 버전 (`1.5.1`).
 * @param latest 최신 버전 조회 상태.
 */
data class MoreUiState(
    val currentVersion: String,
    val latest: LatestVersion = LatestVersion.Loading,
) {
    /** 최신 버전이 현재보다 높으면 true — 파란 강조 · 업데이트 안내. */
    val updateAvailable: Boolean
        get() = (latest as? LatestVersion.Loaded)?.let { compareVersions(it.version, currentVersion) > 0 } ?: false
}

/** 최신 버전 조회 상태. */
sealed interface LatestVersion {
    data object Loading : LatestVersion

    /** 조회 실패 (네트워크 · GitHub rate limit 등). */
    data object Unavailable : LatestVersion

    /** @param version `1.5.2` 형식 (앞의 `v` 제거). */
    data class Loaded(val version: String) : LatestVersion
}

/**
 * `major.minor.patch` 비교. 숫자가 아닌 조각은 0 으로, 빠진 자리도 0 으로 본다 (`1.5` == `1.5.0`).
 *
 * @return [a] 가 크면 양수, 같으면 0, 작으면 음수.
 */
internal fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
    val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
        if (diff != 0) return diff
    }
    return 0
}
