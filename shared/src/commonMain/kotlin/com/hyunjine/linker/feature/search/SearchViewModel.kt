package com.hyunjine.linker.feature.search

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.AnniversariesRepository
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.designsystem.theme.CalendarPurple
import com.hyunjine.linker.designsystem.theme.calendarColorFor
import com.hyunjine.linker.feature.main.resolveOwnerForViewer
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * SearchScreen 의 state · 도메인 로직. 프로필/파트너 색은 진입 시 한 번 로드,
 * 실제 debounce · 결과 리스트는 UI 층 (SearchScreen) 의 snapshotFlow 가 이 VM 의 [search] 를 호출.
 */
class SearchViewModel : ViewModel() {

    private var ownerMe: Color = calendarColorFor(null)
    private var ownerPartner: Color = calendarColorFor("pink")
    private val ownerUs: Color = CalendarPurple
    private var viewerId: String? = null

    init {
        viewModelScope.launch {
            val myProfile = runCatching { UsersRepository.myProfile() }.getOrNull()
            val partner = runCatching { UsersRepository.partnerProfile()?.calendarColor }.getOrNull()
            ownerMe = calendarColorFor(myProfile?.calendarColor)
            ownerPartner = calendarColorFor(partner ?: "pink")
            viewerId = myProfile?.id
        }
    }

    /** 스케줄 · 기념일 병렬 검색. debounce · 빈 문자열 처리는 UI 층 담당. */
    suspend fun search(query: String): SearchResults {
        val schedules = runCatching { SchedulesRepository.search(query) }
            .onFailure { println("[Search] schedules 실패: $it") }
            .getOrDefault(emptyList())
            .map { row ->
                val resolved = resolveOwnerForViewer(row.ownerKind, row.createdBy, viewerId)
                SearchScheduleItem(
                    id = row.id,
                    title = row.title,
                    date = LocalDate.parse(row.startDate),
                    ownerColor = ownerColorFor(resolved),
                )
            }
        val anniversaries = runCatching { AnniversariesRepository.search(query) }
            .onFailure { println("[Search] anniversaries 실패: $it") }
            .getOrDefault(emptyList())
            .map { row ->
                SearchAnniversaryItem(
                    id = row.id,
                    title = row.title,
                    date = LocalDate.parse(row.date),
                    repeatYearly = row.repeatYearly,
                )
            }
        return SearchResults(schedules = schedules, anniversaries = anniversaries)
    }

    private fun ownerColorFor(kind: String): Color = when (kind) {
        "me" -> ownerMe
        "partner" -> ownerPartner
        else -> ownerUs
    }
}
