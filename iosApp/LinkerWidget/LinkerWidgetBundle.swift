import WidgetKit
import SwiftUI

/// 위젯 번들 진입점. WidgetKit 은 여기서 사용 가능한 위젯 목록을 얻는다.
/// - TodayScheduleWidget: Home (systemSmall) + Lock (accessoryRectangular/Inline/Circular)
/// - SplitWidget: Home systemMedium (좌: 오늘 일정 · 우: 미완료 할 일 누적, #244)
/// - CalendarWidget: Home systemMedium (좌: 이번 달 미니 달력 · 우: 오늘 할 일 · 일정, #244)
/// - DdayHomeWidget: Home small/medium D-day 카운터 (#329)
/// - DdayLockWidget: Lock accessoryCircular D-day 카운터 (#329)
@main
struct LinkerWidgetBundle: WidgetBundle {
    var body: some Widget {
        TodayScheduleWidget()
        SplitWidget()
        CalendarWidget()
        DdayHomeWidget()
        DdayLockWidget()
    }
}
