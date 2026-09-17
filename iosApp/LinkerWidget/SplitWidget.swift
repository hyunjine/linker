import WidgetKit
import SwiftUI

// MARK: - Timeline

/// 4×2 split 위젯 (#244) 의 timeline entry. 좌측은 오늘의 일정, 우측은 미완료 할 일 (누적).
struct SplitEntry: TimelineEntry {
    let date: Date
    let payload: WidgetTodayPayload?
}

/// TodayScheduleWidget 과 동일한 App Group JSON 을 소스로 쓴다.
/// 우측 컬럼은 `payload.openTasks` — 지난 날짜의 미완료 할 일도 포함해 계속 누적.
struct SplitProvider: TimelineProvider {
    func placeholder(in context: Context) -> SplitEntry {
        SplitEntry(date: Date(), payload: SplitSample.filled)
    }

    func getSnapshot(in context: Context, completion: @escaping (SplitEntry) -> Void) {
        completion(SplitEntry(date: Date(), payload: validatedPayload() ?? SplitSample.filled))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SplitEntry>) -> Void) {
        let now = Date()
        let entry = SplitEntry(date: now, payload: validatedPayload())
        // 자정에 새 date 헤더 · 오늘 일정을 다시 로드. 미완료 할 일은 계속 누적이라 별도 만료 없음.
        let midnight = Calendar.current.nextDate(
            after: now,
            matching: DateComponents(hour: 0, minute: 0, second: 0),
            matchingPolicy: .nextTime,
        ) ?? now.addingTimeInterval(60 * 60 * 6)
        completion(Timeline(entries: [entry], policy: .after(midnight)))
    }

    /// TodayScheduleWidget 와 동일 파일. `date` 가 오늘과 다르면 좌측 컬럼은 stale 로 판단해 nil 처리
    /// (앱이 아직 refresh 를 못 돌린 상태). 우측 openTasks 는 stale 여도 표시할지 여부가 애매하지만,
    /// payload 전체를 nil 로 두면 위젯이 "설정 필요" 톤으로 노출돼 사용자에게 신호가 명확함.
    private func validatedPayload() -> WidgetTodayPayload? {
        guard let payload = SharedTodayStore.read() else { return nil }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = TimeZone.current
        let today = f.string(from: Date())
        return payload.date == today ? payload : nil
    }
}

// MARK: - Widget

/// 4×2 split 위젯. 홈스크린 `.systemMedium` 만 지원 (좌·우 컬럼 레이아웃이 정보 목적).
struct SplitWidget: Widget {
    let kind: String = "SplitWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SplitProvider()) { entry in
            SplitView(entry: entry)
                .containerBackground(for: .widget) {
                    Rectangle().fill(.ultraThinMaterial)
                }
        }
        .configurationDisplayName("일정 · 할 일")
        .description("왼쪽엔 오늘 일정, 오른쪽엔 미완료 할 일을 한눈에.")
        .supportedFamilies([.systemMedium])
    }
}

// MARK: - Views

private struct SplitView: View {
    let entry: SplitEntry
    var body: some View {
        let colors = OwnerColors(payload: entry.payload)
        let schedules = (entry.payload?.items ?? []).filter { !$0.isTask }
        let tasks = entry.payload?.openTasks ?? []
        HStack(alignment: .top, spacing: 12) {
            SplitColumn(title: scheduleHeader, empty: "일정 없음") {
                if schedules.isEmpty {
                    Text("일정 없음").font(.caption).foregroundStyle(.secondary)
                } else {
                    AdaptiveList(count: schedules.count) { shown, hidden in
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(schedules.prefix(shown)) { s in
                                ScheduleCell(item: s, colors: colors)
                            }
                            if hidden > 0 { OverflowLabel(count: hidden) }
                        }
                    }
                }
            }
            SplitColumn(title: nil, empty: "할 일 없음") {
                if tasks.isEmpty {
                    Text("할 일 없음").font(.caption).foregroundStyle(.secondary)
                } else {
                    AdaptiveList(count: tasks.count) { shown, hidden in
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(tasks.prefix(shown)) { t in
                                OpenTaskCell(item: t, colors: colors)
                            }
                            if hidden > 0 { OverflowLabel(count: hidden) }
                        }
                    }
                }
            }
        }
    }

    private var scheduleHeader: String {
        let f = DateFormatter(); f.dateFormat = "M월 d일 (E)"; f.locale = Locale(identifier: "ko_KR")
        return f.string(from: entry.date)
    }
}

/// 좌·우 컬럼 공통 껍데기 (제목 + 컨텐츠 슬롯). 헤더 폰트·spacing 통일.
/// title 이 nil 이면 헤더 텍스트는 감추되 **자리는 유지** — 좌우 컬럼 컨텐츠 상단 라인을
/// 정렬하기 위한 트릭 (우측 컬럼에서 헤더 라벨을 없애도 좌측 date 헤더와 세로 정렬 유지).
private struct SplitColumn<Content: View>: View {
    let title: String?
    let empty: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title ?? " ")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .opacity(title == nil ? 0 : 1)
            content()
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// 위젯 세로 공간이 허용하는 만큼 row 를 채우고 넘치면 `+N` 배지로 축약.
/// `ViewThatFits` 는 앞에서부터 fit 되는 첫 후보를 채택 → hidden=0 (전부 노출) 부터 시도.
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

private struct OverflowLabel: View {
    let count: Int
    var body: some View {
        Text("+\(count)개")
            .font(.caption2)
            .foregroundStyle(.secondary)
    }
}

private struct ScheduleCell: View {
    let item: WidgetSchedule
    let colors: OwnerColors
    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(colors.color(for: item.ownerKind))
                .frame(width: 6, height: 6)
            if let t = item.timeLabel {
                Text(t)
                    .font(.caption2)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }
            Text(item.title)
                .font(.caption)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
    }
}

private struct OpenTaskCell: View {
    let item: WidgetOpenTask
    let colors: OwnerColors
    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(colors.color(for: item.ownerKind))
                .frame(width: 6, height: 6)
            Text(item.title)
                .font(.caption)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
    }
}

// MARK: - Previews

private enum SplitSample {
    static var filled: WidgetTodayPayload {
        WidgetTodayPayload(
            date: todayString(),
            items: [
                WidgetSchedule(id: "s1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
                WidgetSchedule(id: "s2", title: "저녁 약속", timeLabel: "오후 7:00", ownerKind: "partner", isTask: false, isDone: false),
            ],
            openTasks: [
                WidgetOpenTask(id: "t1", title: "장보기", startDate: yesterdayString(), ownerKind: "us"),
                WidgetOpenTask(id: "t2", title: "우편물 픽업", startDate: todayString(), ownerKind: "me"),
                WidgetOpenTask(id: "t3", title: "세탁소 맡기기", startDate: todayString(), ownerKind: "partner"),
            ],
        )
    }

    static var scheduleOnly: WidgetTodayPayload {
        WidgetTodayPayload(
            date: todayString(),
            items: [
                WidgetSchedule(id: "s1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
            ],
            openTasks: [],
        )
    }

    static var taskOnly: WidgetTodayPayload {
        WidgetTodayPayload(
            date: todayString(),
            items: [],
            openTasks: [
                WidgetOpenTask(id: "t1", title: "장보기", startDate: yesterdayString(), ownerKind: "us"),
                WidgetOpenTask(id: "t2", title: "우편물 픽업", startDate: todayString(), ownerKind: "me"),
            ],
        )
    }

    static var empty: WidgetTodayPayload {
        WidgetTodayPayload(date: todayString(), items: [], openTasks: [])
    }

    static var many: WidgetTodayPayload {
        WidgetTodayPayload(
            date: todayString(),
            items: [
                WidgetSchedule(id: "s1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
                WidgetSchedule(id: "s2", title: "점심 미팅", timeLabel: "오후 12:30", ownerKind: "me", isTask: false, isDone: false),
                WidgetSchedule(id: "s3", title: "저녁 약속", timeLabel: "오후 7:00", ownerKind: "partner", isTask: false, isDone: false),
                WidgetSchedule(id: "s4", title: "운동", timeLabel: "오후 9:00", ownerKind: "me", isTask: false, isDone: false),
            ],
            openTasks: [
                WidgetOpenTask(id: "t1", title: "장보기", startDate: yesterdayString(), ownerKind: "us"),
                WidgetOpenTask(id: "t2", title: "택배 픽업", startDate: yesterdayString(), ownerKind: "me"),
                WidgetOpenTask(id: "t3", title: "우편물 확인", startDate: todayString(), ownerKind: "partner"),
                WidgetOpenTask(id: "t4", title: "세탁소", startDate: todayString(), ownerKind: "us"),
                WidgetOpenTask(id: "t5", title: "청소기 필터 교체", startDate: todayString(), ownerKind: "me"),
            ],
        )
    }

    private static func todayString() -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f.string(from: Date())
    }

    private static func yesterdayString() -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date().addingTimeInterval(-86400))
    }
}

#Preview("Split · 일정+할 일", as: .systemMedium) {
    SplitWidget()
} timeline: {
    SplitEntry(date: Date(), payload: SplitSample.filled)
}

#Preview("Split · 일정만", as: .systemMedium) {
    SplitWidget()
} timeline: {
    SplitEntry(date: Date(), payload: SplitSample.scheduleOnly)
}

#Preview("Split · 할 일만", as: .systemMedium) {
    SplitWidget()
} timeline: {
    SplitEntry(date: Date(), payload: SplitSample.taskOnly)
}

#Preview("Split · 빈 상태", as: .systemMedium) {
    SplitWidget()
} timeline: {
    SplitEntry(date: Date(), payload: SplitSample.empty)
}

#Preview("Split · +N 오버플로", as: .systemMedium) {
    SplitWidget()
} timeline: {
    SplitEntry(date: Date(), payload: SplitSample.many)
}
