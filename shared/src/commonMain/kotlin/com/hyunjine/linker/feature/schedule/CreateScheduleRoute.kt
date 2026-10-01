package com.hyunjine.linker.feature.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyunjine.linker.auth.rememberOutlookAuthClient
import com.hyunjine.linker.data.remote.OutlookSyncService
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 스케줄 생성 · 편집 라우트. [scheduleId] 있으면 편집. [initialDate] · [initialType] 은 신규 진입 seed.
 * 저장/삭제 완료 시 [onDone] 콜백 (App 이 pop).
 *
 * 신규 case: initial 을 Route 에서 sync 로 즉시 만들어 Screen 에 넘김 (VM 우회) — 이렇게 해야
 * Screen 이 mount 되는 순간 initial 이 이미 확정돼 rememberSaveable 이 첫 값을 정확히 잡는다.
 * 편집 case: VM 이 DB 에서 prefetch → uiState.loaded=true 후 mount.
 *
 * 편집 대상이 `source='outlook'` (재택 미러, #242) 이면 Supabase 저장/삭제 완료 후 원본 Outlook 일정도
 * [OutlookSyncService.pushEdit] · [OutlookSyncService.pushDelete] 로 반영 → 파트너 화면 + 원본 양쪽 일치.
 * Graph 요청 실패는 로그만 남기고 UI flow 는 계속 진행 (미러는 다음 sync 때 원본으로 다시 덮어써질 수 있음).
 */
@Composable
fun CreateScheduleRoute(
    scheduleId: String? = null,
    initialDate: String? = null,
    initialType: String? = null,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel: CreateScheduleViewModel = viewModel(key = scheduleId ?: "new") {
        CreateScheduleViewModel(scheduleId)
    }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val outlookAuth = rememberOutlookAuthClient()
    val outlookSync = remember(outlookAuth) { OutlookSyncService(outlookAuth) }
    val bgScope = rememberCoroutineScope()

    if (viewModel.editing) {
        // 매 mount 마다 fresh 조회 — 저장 후 재편집해도 stale 값 안 보이도록. VM 이 재사용되는
        // 케이스 (nav3 백스택 재구성 등) 를 방어.
        LaunchedEffect(scheduleId) { viewModel.reloadFromDb() }
        // DB fetch 완료 대기.
        if (!ui.loaded) return
        val original = ui.initial
        CreateScheduleScreen(
            initial = original,
            editing = true,
            onBack = onBack,
            onSave = { draft, scope ->
                viewModel.save(draft, scope) {
                    if (original?.source == "outlook" && original.externalId != null) {
                        // Supabase 는 이미 갱신됨. 원본 Outlook 재택 일정도 반영해서 (#242)
                        // 다음 sync 때 서버 상태가 튀지 않도록 유지. 제목 · 날짜 · 시각만 바꾸고 나머지는 유지.
                        bgScope.launch {
                            outlookSync.pushEdit(
                                externalId = original.externalId,
                                previousDate = original.startDate,
                                newDate = draft.startDate,
                                startTime = draft.startTime.takeUnless { draft.allDay },
                                endTime = draft.endTime.takeUnless { draft.allDay },
                            ).onFailure { println("[Outlook] pushEdit 실패: $it") }
                        }
                    }
                    onDone()
                }
            },
            onDelete = { scope ->
                viewModel.delete(scope) {
                    if (original?.source == "outlook" && original.externalId != null) {
                        bgScope.launch {
                            outlookSync.pushDelete(original.externalId)
                                .onFailure { println("[Outlook] pushDelete 실패: $it") }
                        }
                    }
                    onDone()
                }
            },
            saving = ui.saving,
            deleting = ui.deleting,
        )
    } else {
        // 신규: initialType/initialDate 로 seed 를 여기서 즉시 만들어 넘김.
        // Outlook 이벤트 신규 생성은 앱 밖 Outlook 캘린더에서 하도록 유도 (앱에서 만든 새
        // 이벤트는 항상 source=internal 로 저장 — Supabase 만 반영).
        val initial = remember(initialDate, initialType) {
            buildCreateInitial(initialDate, initialType)
        }
        CreateScheduleScreen(
            initial = initial,
            editing = false,
            onBack = onBack,
            onSave = { draft, scope -> viewModel.save(draft, scope, onDone) },
            onDelete = { scope -> viewModel.delete(scope, onDone) },
            saving = ui.saving,
        )
    }
}

/**
 * initialType/initialDate 로부터 신규 draft seed 생성. 둘 다 null 이면 null 반환 —
 * Screen 이 default (오늘 · Schedule) 로 초기화.
 */
@OptIn(ExperimentalTime::class)
private fun buildCreateInitial(initialDate: String?, initialType: String?): ScheduleDraft? {
    val seededDate = initialDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val seededType = when (initialType) {
        "task" -> ScheduleType.Task
        "schedule" -> ScheduleType.Schedule
        else -> null
    }
    if (seededDate == null && seededType == null) return null
    val date = seededDate ?: today()
    return ScheduleDraft(
        startDate = date,
        endDate = date,
        type = seededType ?: ScheduleType.Schedule,
    )
}

@OptIn(ExperimentalTime::class)
private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
