import WidgetKit
import SwiftUI

// MARK: - Timeline

/// 캘린더 위젯 timeline entry. 왼쪽 미니 달력 + 오른쪽 오늘 할 일 · 일정 리스트.
struct CalendarEntry: TimelineEntry {
    let date: Date
    let payload: WidgetTodayPayload?
}

/// TodayScheduleWidget · SplitWidget 과 동일한 App Group JSON 소스를 공유.
/// 미니 달력 dot 은 `payload.monthEvents` (이번 달 하루 단위 owner 리스트) 를 사용.
struct CalendarProvider: TimelineProvider {
    func placeholder(in context: Context) -> CalendarEntry {
        CalendarEntry(date: Date(), payload: CalendarSample.filled)
    }

    func getSnapshot(in context: Context, completion: @escaping (CalendarEntry) -> Void) {
        completion(CalendarEntry(date: Date(), payload: validatedPayload() ?? CalendarSample.filled))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CalendarEntry>) -> Void) {
        let now = Date()
        let entry = CalendarEntry(date: now, payload: validatedPayload())
        // 자정에 오늘 하이라이트 · 리스트 갱신을 트리거. openTasks 는 누적이라 별도 만료 없음.
        let midnight = Calendar.current.nextDate(
            after: now,
            matching: DateComponents(hour: 0, minute: 0, second: 0),
            matchingPolicy: .nextTime,
        ) ?? now.addingTimeInterval(60 * 60 * 6)
        completion(Timeline(entries: [entry], policy: .after(midnight)))
    }

    /// payload.date 가 오늘이 아니면 stale 로 판단해 nil 처리 (앱이 아직 refresh 못 돌린 상태).
    /// TodayScheduleWidget · SplitWidget 과 동일한 정책.
    private func validatedPayload() -> WidgetTodayPayload? {
        guard let payload = SharedTodayStore.read() else { return nil }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = TimeZone.current
        let today = f.string(from: Date())
        return payload.date == today ? payload : nil
    }
}

// MARK: - Widget

/// 캘린더 위젯. 홈스크린 `.systemMedium` 만 지원 (좌: 미니 달력 · 우: 오늘 할 일·일정).
struct CalendarWidget: Widget {
    let kind: String = "CalendarWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: CalendarProvider()) { entry in
            CalendarView(entry: entry)
                // #305 — `.ultraThinMaterial` 는 반투명이라 홈스크린 배경 이미지가 비쳐
                // 그림자 얹힌 것처럼 보였음. SplitWidget 톤과 통일해 흰색 단색으로 고정.
                .containerBackground(for: .widget) {
                    Color.white
                }
        }
        .configurationDisplayName("달력 · 할 일 · 일정")
        .description("이번 달 한눈에, 오늘 할 일과 일정도 함께.")
        .supportedFamilies([.systemMedium])
    }
}

// MARK: - Views

private struct CalendarView: View {
    let entry: CalendarEntry
    var body: some View {
        let colors = OwnerColors(payload: entry.payload)
        HStack(alignment: .top, spacing: 12) {
            MiniCalendarView(
                today: entry.date,
                colors: colors,
                monthEvents: entry.payload?.monthEvents ?? [:],
                holidays: Set(entry.payload?.holidays ?? []),
            )
            VStack(alignment: .leading, spacing: 2) {
                // 미니 달력의 "월" 라벨 자리만큼 우측을 밀어, 리스트 첫 줄이 좌측 요일 헤더
                // (일 월 화 …) 라인과 정렬되도록. hidden 이라 보이진 않지만 자리는 유지.
                Text("9월")
                    .font(.system(size: 11, weight: .semibold))
                    .hidden()
                    .accessibilityHidden(true)
                MergedListView(entry: entry, colors: colors)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

// MARK: - Mini calendar

/// 이번 달 5주 grid. 지난달 · 다음달로 넘어가는 셀은 fade, 오늘은 파란 원 하이라이트,
/// 이벤트 있는 날은 하단에 owner dot (최대 3개).
///
/// 6주 필요한 달은 마지막 주가 잘리는 걸 감수 — 위젯 세로 공간 제약. 5주 grid 로 통일해
/// 셀 크기 · 폰트 사이즈를 안정적으로 유지한다.
private struct MiniCalendarView: View {
    let today: Date
    let colors: OwnerColors
    let monthEvents: [String: [String]]
    /// 이번 달 공휴일 "yyyy-MM-dd" 집합 (#309). 셀 번호 컬러 결정에 사용.
    let holidays: Set<String>

    private var cal: Calendar { Calendar(identifier: .gregorian) }
    private var isoFormatter: DateFormatter {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = TimeZone.current
        return f
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            monthHeader
            weekdayHeader
            ForEach(weeks.indices, id: \.self) { i in
                weekRow(weeks[i])
            }
            Spacer(minLength: 0)
        }
    }

    private var monthLabel: String {
        let f = DateFormatter(); f.dateFormat = "M월"; f.locale = Locale(identifier: "ko_KR")
        return f.string(from: today)
    }

    /// 월 라벨. 첫 요일 셀 안에서 center 정렬해 아래 "일" 요일 헤더와 x 정렬 일치.
    private var monthHeader: some View {
        HStack(spacing: 0) {
            Text(monthLabel)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(.primary.opacity(0.88))
                .frame(maxWidth: .infinity)
            ForEach(1..<7, id: \.self) { _ in
                Color.clear.frame(maxWidth: .infinity)
            }
        }
    }

    /// 일 ~ 토 헤더. 일요일 빨강, 토요일 파랑 강조.
    private var weekdayHeader: some View {
        let names = ["일", "월", "화", "수", "목", "금", "토"]
        return HStack(spacing: 0) {
            ForEach(0..<7, id: \.self) { i in
                Text(names[i])
                    .font(.system(size: 8, weight: .medium))
                    .foregroundStyle(weekdayColor(i))
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func weekdayColor(_ i: Int) -> Color {
        switch i {
        case 0: return .red.opacity(0.7)
        case 6: return .blue.opacity(0.7)
        default: return .secondary.opacity(0.7)
        }
    }

    /// 5주 × 7일 grid. 첫 주 앞부분과 마지막 주 뒷부분은 인접 달로 채운다.
    private var weeks: [[Date]] {
        let year = cal.component(.year, from: today)
        let month = cal.component(.month, from: today)
        let firstOfMonth = cal.date(from: DateComponents(year: year, month: month, day: 1)) ?? today
        let leading = cal.component(.weekday, from: firstOfMonth) - 1 // 1=일 → 0 leading
        let start = cal.date(byAdding: .day, value: -leading, to: firstOfMonth) ?? firstOfMonth
        var out: [[Date]] = []
        var cursor = start
        for _ in 0..<5 {
            var week: [Date] = []
            for _ in 0..<7 {
                week.append(cursor)
                cursor = cal.date(byAdding: .day, value: 1, to: cursor) ?? cursor
            }
            out.append(week)
        }
        return out
    }

    private func weekRow(_ week: [Date]) -> some View {
        HStack(spacing: 0) {
            ForEach(week, id: \.self) { d in
                dayCell(d)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func dayCell(_ date: Date) -> some View {
        let dayNum = cal.component(.day, from: date)
        let inMonth = cal.isDate(date, equalTo: today, toGranularity: .month)
        let isToday = cal.isDateInToday(date)
        let iso = isoFormatter.string(from: date)
        let owners = inMonth ? (monthEvents[iso] ?? []) : []
        // 1=일 ~ 7=토. 요일별 · 공휴일별 컬러 결정에 사용 (#309).
        let weekday = cal.component(.weekday, from: date)
        let isHoliday = inMonth && holidays.contains(iso)

        return VStack(spacing: 1) {
            ZStack {
                if isToday {
                    Circle().fill(Color.blue).frame(width: 14, height: 14)
                }
                Text("\(dayNum)")
                    .font(.system(size: 10, weight: isToday ? .semibold : .regular))
                    .foregroundStyle(dayColor(
                        inMonth: inMonth,
                        isToday: isToday,
                        weekday: weekday,
                        isHoliday: isHoliday,
                    ))
            }
            .frame(height: 14)
            HStack(spacing: 1) {
                ForEach(Array(owners.prefix(3)).indices, id: \.self) { idx in
                    Circle().fill(colors.color(for: owners[idx])).frame(width: 3, height: 3)
                }
            }
            .frame(height: 3)
        }
        .padding(.vertical, 1)
    }

    /// 셀 번호 컬러. 오늘(파란 원 위) → 흰색. 그 외에는 공휴일·일요일=빨강, 토요일=파랑, 평일=기본톤.
    /// 다른 달로 넘어간 셀은 원본 톤을 유지하면서 opacity 로 페이드 (요일 컬러도 눈에 띄지 않게).
    private func dayColor(inMonth: Bool, isToday: Bool, weekday: Int, isHoliday: Bool) -> Color {
        if isToday { return .white }
        let base: Color
        if isHoliday || weekday == 1 {        // 공휴일 또는 일요일
            base = .red
        } else if weekday == 7 {              // 토요일
            base = .blue
        } else {
            base = .primary
        }
        return base.opacity(inMonth ? 0.88 : 0.25)
    }
}

// MARK: - Merged list (tasks priority → schedules)

/// 우측 컬럼. 미완료 할 일 (openTasks, 우선) + 오늘 일정 (items 중 !isTask) 을 한 리스트로.
/// 시각 유무로 두 종류가 자연스레 구분되므로 별도 sub-header 없음.
/// SplitWidget 과 동일한 AdaptiveList 오버플로 처리 (`+N 개`).
private struct MergedListView: View {
    let entry: CalendarEntry
    let colors: OwnerColors

    var body: some View {
        let rows = buildRows()
        VStack(alignment: .leading, spacing: 0) {
            if rows.isEmpty {
                Text("오늘 비어있어요")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                AdaptiveList(count: rows.count) { shown, hidden in
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(rows.prefix(shown)) { r in
                            MergedRowCell(row: r, colors: colors)
                        }
                        if hidden > 0 {
                            Text("+\(hidden)개")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func buildRows() -> [MergedRow] {
        let tasks = (entry.payload?.openTasks ?? []).map {
            MergedRow(id: "t-\($0.id)", title: $0.title, timeLabel: nil, ownerKind: $0.ownerKind)
        }
        let scheds = (entry.payload?.items ?? [])
            .filter { !$0.isTask }
            .map { MergedRow(id: "s-\($0.id)", title: $0.title, timeLabel: $0.timeLabel, ownerKind: $0.ownerKind) }
        return tasks + scheds
    }
}

private struct MergedRow: Identifiable, Hashable {
    let id: String
    let title: String
    let timeLabel: String?
    let ownerKind: String
}

private struct MergedRowCell: View {
    let row: MergedRow
    let colors: OwnerColors
    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(colors.color(for: row.ownerKind)).frame(width: 6, height: 6)
            if let t = row.timeLabel {
                Text(t)
                    .font(.caption2)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }
            Text(row.title)
                .font(.caption)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
    }
}

/// 위젯 세로 공간이 허용하는 만큼 row 를 채우고 넘치면 `+N` 배지로 축약.
/// SplitWidget 의 동명 struct 와 별도로 두 위젯이 독립 컴파일되도록 이 파일에도 사본을 둔다.
private struct AdaptiveList<Content: View>: View {
    let count: Int
    @ViewBuilder let content: (_ shown: Int, _ hidden: Int) -> Content
    var body: some View {
        ViewThatFits(in: .vertical) {
            ForEach(0..<count, id: \.self) { hidden in
                content(count - hidden, hidden)
            }
        }
    }
}

// MARK: - Previews

private enum CalendarSample {
    static var filled: WidgetTodayPayload {
        WidgetTodayPayload(
            date: todayString(),
            items: [
                WidgetSchedule(id: "s1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
                WidgetSchedule(id: "s2", title: "저녁 약속", timeLabel: "오후 07:00", ownerKind: "partner", isTask: false, isDone: false),
            ],
            openTasks: [
                WidgetOpenTask(id: "t1", title: "장보기", startDate: yesterdayString(), ownerKind: "us"),
                WidgetOpenTask(id: "t2", title: "우편물 픽업", startDate: todayString(), ownerKind: "me"),
                WidgetOpenTask(id: "t3", title: "세탁소 맡기기", startDate: todayString(), ownerKind: "partner"),
            ],
            monthEvents: sampleMonthEvents(),
        )
    }

    static var tasksOnly: WidgetTodayPayload {
        WidgetTodayPayload(
            date: todayString(),
            items: [],
            openTasks: [
                WidgetOpenTask(id: "t1", title: "장보기", startDate: yesterdayString(), ownerKind: "us"),
                WidgetOpenTask(id: "t2", title: "우편물 픽업", startDate: todayString(), ownerKind: "me"),
                WidgetOpenTask(id: "t3", title: "세탁소", startDate: todayString(), ownerKind: "partner"),
                WidgetOpenTask(id: "t4", title: "청소기 필터", startDate: todayString(), ownerKind: "me"),
            ],
            monthEvents: sampleMonthEvents(),
        )
    }

    static var schedulesOnly: WidgetTodayPayload {
        WidgetTodayPayload(
            date: todayString(),
            items: [
                WidgetSchedule(id: "s1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
                WidgetSchedule(id: "s2", title: "점심 미팅", timeLabel: "오후 12:30", ownerKind: "me", isTask: false, isDone: false),
                WidgetSchedule(id: "s3", title: "저녁 약속", timeLabel: "오후 07:00", ownerKind: "partner", isTask: false, isDone: false),
            ],
            openTasks: [],
            monthEvents: sampleMonthEvents(),
        )
    }

    static var empty: WidgetTodayPayload {
        WidgetTodayPayload(date: todayString(), items: [], openTasks: [], monthEvents: [:])
    }

    static var many: WidgetTodayPayload {
        WidgetTodayPayload(
            date: todayString(),
            items: [
                WidgetSchedule(id: "s1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
                WidgetSchedule(id: "s2", title: "점심 미팅", timeLabel: "오후 12:30", ownerKind: "me", isTask: false, isDone: false),
                WidgetSchedule(id: "s3", title: "저녁 약속", timeLabel: "오후 07:00", ownerKind: "partner", isTask: false, isDone: false),
            ],
            openTasks: [
                WidgetOpenTask(id: "t1", title: "장보기", startDate: yesterdayString(), ownerKind: "us"),
                WidgetOpenTask(id: "t2", title: "택배 픽업", startDate: yesterdayString(), ownerKind: "me"),
                WidgetOpenTask(id: "t3", title: "우편물 확인", startDate: todayString(), ownerKind: "partner"),
                WidgetOpenTask(id: "t4", title: "세탁소", startDate: todayString(), ownerKind: "us"),
                WidgetOpenTask(id: "t5", title: "청소기 필터", startDate: todayString(), ownerKind: "me"),
            ],
            monthEvents: sampleMonthEvents(),
        )
    }

    private static func todayString() -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f.string(from: Date())
    }

    private static func yesterdayString() -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date().addingTimeInterval(-86400))
    }

    /// 오늘 + 오늘 근처 며칠에 이벤트가 있다고 가정한 샘플. 미니 달력 dot 을 preview 에서 확인용.
    private static func sampleMonthEvents() -> [String: [String]] {
        let cal = Calendar.current
        let today = Date()
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        var out: [String: [String]] = [:]
        for delta in [-9, -5, 0, 3, 8] {
            if let d = cal.date(byAdding: .day, value: delta, to: today) {
                out[f.string(from: d)] = delta == 0 ? ["me", "partner"] : (delta % 2 == 0 ? ["us"] : ["me"])
            }
        }
        return out
    }
}

#Preview("Calendar · 할 일 + 일정", as: .systemMedium) {
    CalendarWidget()
} timeline: {
    CalendarEntry(date: Date(), payload: CalendarSample.filled)
}

#Preview("Calendar · 할 일만", as: .systemMedium) {
    CalendarWidget()
} timeline: {
    CalendarEntry(date: Date(), payload: CalendarSample.tasksOnly)
}

#Preview("Calendar · 일정만", as: .systemMedium) {
    CalendarWidget()
} timeline: {
    CalendarEntry(date: Date(), payload: CalendarSample.schedulesOnly)
}

#Preview("Calendar · 빈 상태", as: .systemMedium) {
    CalendarWidget()
} timeline: {
    CalendarEntry(date: Date(), payload: CalendarSample.empty)
}

#Preview("Calendar · +N 오버플로", as: .systemMedium) {
    CalendarWidget()
} timeline: {
    CalendarEntry(date: Date(), payload: CalendarSample.many)
}
