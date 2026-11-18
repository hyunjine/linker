package com.hyunjine.linker.feature.photo

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.CouplePhotosRepository
import com.hyunjine.linker.data.remote.CoupleRealtimeSubscription
import com.hyunjine.linker.data.remote.CouplesRepository
import com.hyunjine.linker.data.remote.SupabaseProvider
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.data.remote.subscribeCoupleRealtime
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * 사진첩 화면 state · 도메인 로직. 목록 로드 · 업로드 · 실패 재시도.
 *
 * 삭제는 전체 보기 화면 ([PhotoViewerViewModel]) 이 담당하고, 여기서는 realtime 으로 받은
 * 변경을 다시 로드해 그리드에 반영한다.
 */
class PhotosViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PhotosUiState())
    val uiState: StateFlow<PhotosUiState> = _uiState.asStateFlow()

    /** 업로드가 실패한 이미지 · 날짜. "다시 시도" 가 이 값으로 재업로드한다. */
    private var pendingRetry: Pair<ImageBitmap, LocalDate>? = null

    private val realtimeJob: Job = viewModelScope.subscribeCoupleRealtime(
        CoupleRealtimeSubscription(
            onPhotosChanged = { reload() },
            // 커플이 새로 연결되면 미연결 안내 → 그리드로 바뀌어야 한다.
            onCoupleChanged = { reload() },
        ),
    )

    init { reload() }

    override fun onCleared() {
        super.onCleared()
        realtimeJob.cancel()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val coupleLinked = runCatching { CouplesRepository.myCoupleIdOrNull() != null }
                .getOrDefault(false)
            if (!coupleLinked) {
                _uiState.update {
                    it.copy(loading = false, coupleLinked = false, sections = emptyList())
                }
                return@launch
            }
            val myId = SupabaseProvider.client.auth.currentUserOrNull()?.id
            val partnerNickname = runCatching { UsersRepository.partnerProfile()?.nickname }
                .getOrNull()
            val rows = runCatching { CouplePhotosRepository.list() }
                .onFailure { println("[Photo] list 실패: $it") }
                .getOrDefault(emptyList())
            val photos = rows.toPhotoUiWithThumbs(myId, partnerNickname)
            _uiState.update {
                it.copy(loading = false, coupleLinked = true, sections = photos.toMonthSections())
            }
        }
    }

    /**
     * 사진 업로드. 성공하면 목록을 다시 로드하고 [PhotosUiState.uploadedTick] 을 올려 화면이
     * 날짜 확인 시트를 닫게 한다. 실패하면 [PhotosUiState.failed] 를 세우고 이미지를 들고 있어
     * 같은 자리에서 바로 다시 시도할 수 있게 한다.
     */
    fun upload(image: ImageBitmap, takenOn: LocalDate) {
        if (_uiState.value.uploading) return
        pendingRetry = image to takenOn
        _uiState.update { it.copy(uploading = true, failed = false) }
        viewModelScope.launch {
            runCatching { CouplePhotosRepository.upload(image, takenOn) }
                .onSuccess { id ->
                    println("[Photo] 업로드 성공: $id")
                    pendingRetry = null
                    _uiState.update { s ->
                        s.copy(uploading = false, failed = false, uploadedTick = s.uploadedTick + 1)
                    }
                    reload()
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    println("[Photo] 업로드 실패: $it")
                    _uiState.update { s -> s.copy(uploading = false, failed = true) }
                }
        }
    }

    /** 실패한 업로드 재시도. 보관된 이미지가 없으면 no-op. */
    fun retryUpload() {
        val (image, date) = pendingRetry ?: return
        upload(image, date)
    }

    /** 실패 배너 닫기. 보관한 이미지도 버린다. */
    fun dismissFailure() {
        pendingRetry = null
        _uiState.update { it.copy(failed = false) }
    }
}

/**
 * @param coupleLinked 커플 연결 여부. false 면 업로드 대신 커플 연결 안내를 보여준다.
 * @param failed 마지막 업로드가 실패했는지.
 * @param uploadedTick 업로드 성공 횟수. 값이 올라가면 화면이 날짜 확인 시트를 닫는다.
 */
data class PhotosUiState(
    val sections: List<PhotoMonthSection> = emptyList(),
    val loading: Boolean = true,
    val uploading: Boolean = false,
    val failed: Boolean = false,
    val coupleLinked: Boolean = true,
    val uploadedTick: Int = 0,
)
