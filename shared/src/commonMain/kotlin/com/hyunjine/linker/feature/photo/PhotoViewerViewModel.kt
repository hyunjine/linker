package com.hyunjine.linker.feature.photo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.CouplePhotosRepository
import com.hyunjine.linker.data.remote.SupabaseProvider
import com.hyunjine.linker.data.remote.UsersRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 사진 전체 보기 화면 state.
 *
 * [photoId] 로 row 를 다시 조회하는 이유: 사진첩 · 날짜 상세 두 진입 경로가 각각 다른 목록을
 * 들고 있어서, 화면 간에 객체를 넘기면 두 경로를 다 뚫어야 한다. nav 로는 id 만 넘긴다.
 */
class PhotoViewerViewModel(private val photoId: String) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoViewerUiState())
    val uiState: StateFlow<PhotoViewerUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val row = runCatching { CouplePhotosRepository.findById(photoId) }
                .onFailure { println("[Photo] findById 실패: $it") }
                .getOrNull()
            if (row == null) {
                _uiState.update { it.copy(loading = false, notFound = true) }
                return@launch
            }
            val myId = SupabaseProvider.client.auth.currentUserOrNull()?.id
            val partnerNickname = runCatching { UsersRepository.partnerProfile()?.nickname }
                .getOrNull()
            val urls = runCatching {
                CouplePhotosRepository.signedUrls(listOf(row.storagePath, row.thumbPath))
            }.onFailure { println("[Photo] signed URL 발급 실패: $it") }
                .getOrDefault(emptyMap())
            _uiState.update {
                it.copy(
                    loading = false,
                    photo = row.toPhotoUi(
                        myUserId = myId,
                        partnerNickname = partnerNickname,
                        thumbUrl = urls[row.thumbPath],
                        originalUrl = urls[row.storagePath],
                    ),
                )
            }
        }
    }

    /**
     * 사진 삭제 (DB row + 원본 · 썸네일 파일). 성공하면 [onDeleted] 로 화면을 닫는다.
     * 실패하면 [PhotoViewerUiState.deleteFailed] 를 세워 다시 시도할 수 있게 한다.
     */
    fun delete(onDeleted: () -> Unit) {
        val photo = _uiState.value.photo ?: return
        if (_uiState.value.deleting || !photo.deletable) return
        _uiState.update { it.copy(deleting = true, deleteFailed = false) }
        viewModelScope.launch {
            val row = runCatching { CouplePhotosRepository.findById(photo.id) }.getOrNull()
            if (row == null) {
                // 이미 지워진 사진 — 화면만 닫으면 된다.
                _uiState.update { it.copy(deleting = false) }
                onDeleted()
                return@launch
            }
            runCatching { CouplePhotosRepository.delete(row) }
                .onSuccess {
                    println("[Photo] 삭제 성공: ${photo.id}")
                    _uiState.update { it.copy(deleting = false) }
                    onDeleted()
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    println("[Photo] 삭제 실패: $it")
                    _uiState.update { s -> s.copy(deleting = false, deleteFailed = true) }
                }
        }
    }
}

data class PhotoViewerUiState(
    val photo: PhotoUi? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val deleting: Boolean = false,
    val deleteFailed: Boolean = false,
)
