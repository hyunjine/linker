package com.hyunjine.linker.feature.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.hyunjine.linker.data.remote.CouplePhotosRepository
import com.hyunjine.linker.designsystem.theme.AvatarPlaceholderBg
import kotlinx.datetime.LocalDate

/**
 * UI 층에서 소비하는 사진 한 장. ViewModel 이 `couple_photos` row + signed URL + 올린 사람
 * 닉네임을 합쳐 만든다.
 *
 * [thumbPath] · [originalPath] 는 Storage 경로. signed URL 은 발급할 때마다 토큰이 달라지므로
 * 이미지 캐시 키로는 URL 이 아니라 이 경로를 쓴다 ([PhotoImage] 참고).
 *
 * @param uploaderLabel "나" 또는 상대 닉네임. 전체 보기 화면에 표시.
 * @param deletable 내가 올린 사진인지. true 일 때만 삭제 버튼을 노출한다 (서버는 RLS 로 강제).
 */
data class PhotoUi(
    val id: String,
    val date: LocalDate,
    val thumbPath: String,
    val thumbUrl: String?,
    val originalPath: String,
    val originalUrl: String?,
    val uploaderLabel: String,
    val deletable: Boolean,
)

/** 사진첩 그리드의 한 달 섹션. [label] 은 "2026년 11월" 형태. */
data class PhotoMonthSection(
    val label: String,
    val photos: List<PhotoUi>,
)

/**
 * 사진 한 장을 그린다.
 *
 * signed URL 을 캐시 키로 쓰면 재발급마다 캐시 미스가 나서 같은 사진을 계속 다시 내려받는다.
 * 캐시 키를 Storage 경로 ([path]) 로 고정해 URL 이 바뀌어도 같은 사진으로 인식시킨다.
 *
 * @param url signed URL. 아직 발급 전 (null) 이면 회색 자리만 보여준다.
 */
@Composable
fun PhotoImage(
    path: String,
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalPlatformContext.current
    if (url == null) {
        Box(modifier = modifier.background(AvatarPlaceholderBg))
        return
    }
    val request = remember(path, url, context) {
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(path)
            .diskCacheKey(path)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.background(AvatarPlaceholderBg),
    )
}

/**
 * `couple_photos` row → [PhotoUi]. 썸네일 signed URL 을 한 번의 요청으로 모두 발급받아 채운다.
 * 원본 URL 은 전체 보기 화면에서만 필요하므로 여기서는 null 로 둔다 (불필요한 발급 방지).
 *
 * @param myUserId 현재 유저 id. 올린 사람 라벨 · 삭제 버튼 노출 판정에 쓴다.
 * @param partnerNickname 파트너 닉네임. null 이면 "상대방" 으로 표시.
 */
suspend fun List<CouplePhotosRepository.Row>.toPhotoUiWithThumbs(
    myUserId: String?,
    partnerNickname: String?,
): List<PhotoUi> {
    if (isEmpty()) return emptyList()
    val urls = runCatching { CouplePhotosRepository.signedUrls(map { it.thumbPath }) }
        .onFailure { println("[Photo] 썸네일 signed URL 발급 실패: $it") }
        .getOrDefault(emptyMap())
    return map { row ->
        row.toPhotoUi(
            myUserId = myUserId,
            partnerNickname = partnerNickname,
            thumbUrl = urls[row.thumbPath],
            originalUrl = null,
        )
    }
}

/** row 한 개 → [PhotoUi]. signed URL 은 호출자가 이미 발급한 것을 넘긴다. */
fun CouplePhotosRepository.Row.toPhotoUi(
    myUserId: String?,
    partnerNickname: String?,
    thumbUrl: String?,
    originalUrl: String?,
): PhotoUi {
    val mine = myUserId != null && uploadedBy == myUserId
    return PhotoUi(
        id = id,
        date = LocalDate.parse(takenOn),
        thumbPath = thumbPath,
        thumbUrl = thumbUrl,
        originalPath = storagePath,
        originalUrl = originalUrl,
        uploaderLabel = if (mine) "나" else partnerNickname?.takeIf { it.isNotBlank() } ?: "상대방",
        deletable = mine,
    )
}

/**
 * 사진 목록을 월별 섹션으로 묶는다. 입력이 이미 최신순 (taken_on desc) 이므로 섹션도 최신 달이
 * 위에 온다 — 리포지토리 정렬에 의존하니 여기서 다시 정렬하지 않는다.
 */
fun List<PhotoUi>.toMonthSections(): List<PhotoMonthSection> =
    groupBy { it.date.year to it.date.month.ordinal + 1 }
        .map { (ym, photos) -> PhotoMonthSection(label = "${ym.first}년 ${ym.second}월", photos = photos) }

/** "2026. 11. 03." 형태. 전체 보기 · 날짜 확인 시트 공통 표기. */
fun formatPhotoDate(date: LocalDate): String {
    val m = (date.month.ordinal + 1).toString().padStart(2, '0')
    val d = date.day.toString().padStart(2, '0')
    return "${date.year}. $m. $d."
}
