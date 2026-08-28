package com.hyunjine.linker.ui.schedule

import androidx.compose.runtime.Composable
import com.hyunjine.linker.ui.common.ListBottomSheet
import kotlinx.datetime.LocalDate

/**
 * 일정 반복 규칙 선택 시트. Figma 2795:79178 톤 — 단순 리스트 [ListBottomSheet] 위에 얹은 얇은 래퍼.
 *
 * 사용자는 6개 옵션 (반복 안함 / 매일 / 매주 / 매월 / 매년 / 사용자 설정) 중 하나를 탭해 선택하며
 * CTA 없이 탭 즉시 [onSelect] 발화. 상위에서 [visible] 을 false 로 바꾸며 시트가 닫힌다.
 *
 * 매주/매월/매년 의 세부 규칙 (요일·일자·월일) 편집 UI 는 후속 이슈 (#15 후속) 로 미룬다. 지금은
 * 유형만 선택하며, 첫 진입 시 [anchorDate] 기준 기본값으로 채운다:
 *  - 매주 → anchor 의 요일
 *  - 매월 → anchor 의 day
 *  - 매년 → anchor 의 month/day
 * 이미 같은 유형이 [current] 로 들어와 있으면 서브 값을 그대로 유지한다.
 *
 * @param visible 시트 표시 여부.
 * @param current 현재 [RepeatRule]. 매칭되는 유형 옆에 체크가 표시된다.
 * @param anchorDate 매주/매월/매년 을 처음 선택했을 때 기본값 산정 기준일 (일반적으로 일정 시작일).
 * @param onSelect 사용자가 유형을 탭했을 때 새 [RepeatRule] 로 호출. 상위에서 draft 갱신 + 시트 닫기.
 * @param onDismiss 드래그 다운·스크림 탭 등 사용자 취소.
 */
@Composable
fun RepeatPickerSheet(
    visible: Boolean,
    current: RepeatRule,
    anchorDate: LocalDate,
    onSelect: (RepeatRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val kinds = RepeatKind.entries
    ListBottomSheet(
        visible = visible,
        options = kinds,
        selected = RepeatKind.of(current),
        onSelect = { kind -> onSelect(kind.toRule(current, anchorDate)) },
        onDismiss = onDismiss,
        label = { it.label },
    )
}

/**
 * [RepeatRule] 을 리스트 UI 용으로 flatten 한 6개 유형. sealed 인 [RepeatRule] 을 그대로 리스트에
 * 넘기면 매주(요일 set) 같은 데이터 클래스 인스턴스마다 equality 가 달라 "선택 상태" 매칭이 깨진다.
 */
private enum class RepeatKind(val label: String) {
    None("반복 안함"),
    Daily("매일"),
    Weekly("매주"),
    Monthly("매월"),
    Yearly("매년"),
    Custom("사용자 설정");

    /** 이 유형으로 draft 전환. 같은 유형이면 서브값 유지, 아니면 anchor 기준 기본값 생성. */
    fun toRule(current: RepeatRule, anchor: LocalDate): RepeatRule = when (this) {
        None -> RepeatRule.None
        Daily -> RepeatRule.Daily
        Weekly -> if (current is RepeatRule.Weekly) current
        else RepeatRule.Weekly(setOf(anchor.dayOfWeek))
        Monthly -> if (current is RepeatRule.Monthly) current
        else RepeatRule.Monthly(anchor.day)
        Yearly -> if (current is RepeatRule.Yearly) current
        else RepeatRule.Yearly(anchor.month.ordinal + 1, anchor.day)
        Custom -> RepeatRule.Custom
    }

    companion object {
        fun of(rule: RepeatRule): RepeatKind = when (rule) {
            RepeatRule.None -> None
            RepeatRule.Daily -> Daily
            is RepeatRule.Weekly -> Weekly
            is RepeatRule.Monthly -> Monthly
            is RepeatRule.Yearly -> Yearly
            RepeatRule.Custom -> Custom
        }
    }
}
