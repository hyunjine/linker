package com.hyunjine.linker.feature.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyunjine.linker.auth.rememberOutlookAuthClient
import com.hyunjine.linker.data.remote.OutlookBodyWrite
import com.hyunjine.linker.data.remote.OutlookDateTime
import com.hyunjine.linker.data.remote.OutlookEventWrite
import com.hyunjine.linker.data.remote.OutlookGraphClient
import com.hyunjine.linker.data.remote.OutlookLocation
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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
 * 편집 대상이 `source='outlook'` 이면 Supabase 저장/삭제 완료 후 Graph API 도 함께 update/delete
 * → 파트너 화면 + 원본 Outlook 캘린더 양쪽에 반영. Graph 요청 실패는 로그만 남기고 UI flow 는
 * 계속 진행 (Supabase mirror 는 다음 sync 사이클에서 다시 원본으로 덮어써질 수 있음).
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
    val outlookGraph = remember(outlookAuth) { OutlookGraphClient(outlookAuth) }
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
                        // Supabase 는 이미 갱신됨. 원본 Outlook 이벤트도 반영해서
                        // 다음 sync 때 서버 상태가 튀지 않도록 유지.
                        bgScope.launch {
                            outlookGraph.updateEvent(original.externalId, draft.toOutlookWrite())
                                .onFailure { println("[Outlook] updateEvent 실패: $it") }
                        }
                    }
                    onDone()
                }
            },
            onDelete = { scope ->
                viewModel.delete(scope) {
                    if (original?.source == "outlook" && original.externalId != null) {
                        bgScope.launch {
                            outlookGraph.deleteEvent(original.externalId)
                                .onFailure { println("[Outlook] deleteEvent 실패: $it") }
                        }
                    }
                    onDone()
                }
            },
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

/**
 * ScheduleDraft → Graph API PATCH body. 종일 여부에 따라 시각을 `T00:00:00` / `startTime` 로 분기.
 * TZ 는 KST 고정 — Graph 는 `Prefer: outlook.timezone` 헤더가 안 걸린 PATCH 요청에서도
 * `timeZone` 필드 값을 기준으로 로컬 시각을 해석하므로 일관성 유지.
 */
private fun ScheduleDraft.toOutlookWrite(): OutlookEventWrite {
    val tz = "Asia/Seoul"
    val startStr: String
    val endStr: String
    if (allDay) {
        // Graph 는 all-day 이벤트도 dateTime 문자열을 요구. 시작 00:00, 종료 익일 00:00 관례.
        startStr = "${startDate}T00:00:00"
        endStr = "${endDate.plus1Day()}T00:00:00"
    } else {
        val st = startTime ?: "00:00"
        val et = endTime ?: st
        startStr = "${startDate}T$st:00"
        endStr = "${endDate}T$et:00"
    }
    return OutlookEventWrite(
        subject = title,
        start = OutlookDateTime(dateTime = startStr, timeZone = tz),
        end = OutlookDateTime(dateTime = endStr, timeZone = tz),
        isAllDay = allDay,
        body = OutlookBodyWrite(content = ""),
        location = OutlookLocation(displayName = null),
    )
}

/** LocalDate +1일 (all-day 이벤트 종료 표기용). */
private fun LocalDate.plus1Day(): LocalDate = plus(1, DateTimeUnit.DAY)
