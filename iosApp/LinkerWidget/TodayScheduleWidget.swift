import WidgetKit
import SwiftUI

// MARK: - Timeline

/// 오늘 일정 위젯의 timeline entry. WidgetKit 이 요청한 시점의 payload snapshot.
struct TodayScheduleEntry: TimelineEntry {
    let date: Date
    let payload: WidgetTodayPayload?
}

/// 오늘 일정 데이터를 App Group 파일에서 읽어 위젯 timeline 을 만든다.
/// - snapshot: 갤러리 · placeholder 에 쓸 즉시 값
/// - timeline: 다음 자정에 자동 refresh 요청 (날짜 넘어가면 payload 도 stale)
struct TodayScheduleProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayScheduleEntry {
        TodayScheduleEntry(date: Date(), payload: samplePayload)
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayScheduleEntry) -> Void) {
        let payload = SharedTodayStore.read() ?? samplePayload
        completion(TodayScheduleEntry(date: Date(), payload: payload))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayScheduleEntry>) -> Void) {
        let now = Date()
        let payload = SharedTodayStore.read()
        let entry = TodayScheduleEntry(date: now, payload: payload)
        // 다음 자정에 다시 로드해 날짜 헤더 · 오늘 items 를 갱신.
        let cal = Calendar.current
        let midnight = cal.nextDate(
            after: now,
            matching: DateComponents(hour: 0, minute: 0, second: 0),
            matchingPolicy: .nextTime,
        ) ?? now.addingTimeInterval(60 * 60 * 6)
        completion(Timeline(entries: [entry], policy: .after(midnight)))
    }

    /// 위젯 갤러리 · 프리뷰용 샘플 데이터. 실제 앱 실행 후에는 App Group 파일이 채움.
    private var samplePayload: WidgetTodayPayload {
        WidgetTodayPayload(
            date: dateString(Date()),
            items: [
                WidgetSchedule(id: "s1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
                WidgetSchedule(id: "s2", title: "장보기", timeLabel: nil, ownerKind: "us", isTask: true, isDone: false),
                WidgetSchedule(id: "s3", title: "저녁 약속", timeLabel: "오후 7:00", ownerKind: "partner", isTask: false, isDone: false),
            ],
        )
    }

    private func dateString(_ date: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f.string(from: date)
    }
}

// MARK: - Widget

/// WidgetKit 위젯 선언. Home Screen (systemSmall/Medium) + Lock Screen (accessoryRectangular/Inline) 지원.
struct TodayScheduleWidget: Widget {
    let kind: String = "TodayScheduleWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TodayScheduleProvider()) { entry in
            TodayScheduleView(entry: entry)
                .containerBackground(.background, for: .widget)
        }
        .configurationDisplayName("오늘 일정")
        .description("링커의 오늘 스케줄과 할 일을 한눈에.")
        .supportedFamilies([
            .systemSmall,
            .systemMedium,
            .accessoryRectangular,
            .accessoryInline,
            .accessoryCircular,
        ])
    }
}

// MARK: - Views

struct TodayScheduleView: View {
    @Environment(\.widgetFamily) var family
    let entry: TodayScheduleEntry

    var body: some View {
        switch family {
        case .accessoryInline:
            InlineView(entry: entry)
        case .accessoryRectangular:
            RectangularView(entry: entry)
        case .accessoryCircular:
            CircularView(entry: entry)
        case .systemSmall:
            SmallView(entry: entry)
        case .systemMedium:
            MediumView(entry: entry)
        default:
            SmallView(entry: entry)
        }
    }
}

private struct InlineView: View {
    let entry: TodayScheduleEntry
    var body: some View {
        let count = entry.payload?.items.count ?? 0
        Text(count == 0 ? "오늘 일정 없음" : "오늘 \(count)개")
    }
}

private struct CircularView: View {
    let entry: TodayScheduleEntry
    var body: some View {
        // 잠금화면 원형은 공간이 극도로 좁아 카운트만 보여줌.
        // AccessoryWidgetBackground 는 iOS 가 잠금화면 톤에 맞춰 반투명 원 배경을 그림.
        let count = entry.payload?.items.count ?? 0
        ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: 0) {
                Text("\(count)")
                    .font(.system(size: 22, weight: .semibold))
                    .monospacedDigit()
                Text("일정")
                    .font(.system(size: 9))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct RectangularView: View {
    let entry: TodayScheduleEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(todayHeader).font(.caption2).foregroundStyle(.secondary)
            if let items = entry.payload?.items, !items.isEmpty {
                ForEach(items.prefix(2)) { item in
                    HStack(spacing: 4) {
                        if let t = item.timeLabel { Text(t).font(.caption2).monospacedDigit() }
                        Text(item.title).font(.caption).lineLimit(1)
                    }
                }
            } else {
                Text("일정 없음").font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private var todayHeader: String {
        let f = DateFormatter(); f.dateFormat = "M월 d일 (E)"; f.locale = Locale(identifier: "ko_KR")
        return f.string(from: entry.date)
    }
}

private struct SmallView: View {
    let entry: TodayScheduleEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(todayHeader).font(.caption).foregroundStyle(.secondary)
                Spacer()
                if let n = entry.payload?.items.count, n > 0 {
                    Text("\(n)").font(.caption2).foregroundStyle(.secondary)
                }
            }
            if let items = entry.payload?.items, !items.isEmpty {
                ForEach(items.prefix(2)) { item in
                    ScheduleRowSmall(item: item)
                }
                if (entry.payload?.items.count ?? 0) > 2 {
                    Text("+\((entry.payload?.items.count ?? 0) - 2)개")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            } else {
                Text("오늘 일정이 없어요")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
    }

    private var todayHeader: String {
        let f = DateFormatter(); f.dateFormat = "M월 d일"; f.locale = Locale(identifier: "ko_KR")
        return f.string(from: entry.date)
    }
}

private struct MediumView: View {
    let entry: TodayScheduleEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(todayHeader).font(.caption).foregroundStyle(.secondary)
                Spacer()
                if let n = entry.payload?.items.count, n > 0 {
                    Text("총 \(n)개").font(.caption2).foregroundStyle(.secondary)
                }
            }
            if let items = entry.payload?.items, !items.isEmpty {
                ForEach(items.prefix(4)) { item in
                    ScheduleRowMedium(item: item)
                }
                if (entry.payload?.items.count ?? 0) > 4 {
                    Text("+\((entry.payload?.items.count ?? 0) - 4)개 더")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            } else {
                Text("오늘 일정이 없어요").font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
    }

    private var todayHeader: String {
        let f = DateFormatter(); f.dateFormat = "M월 d일 (E)"; f.locale = Locale(identifier: "ko_KR")
        return f.string(from: entry.date)
    }
}

private struct ScheduleRowSmall: View {
    let item: WidgetSchedule
    var body: some View {
        HStack(spacing: 6) {
            OwnerDot(kind: item.ownerKind).frame(width: 6, height: 6)
            Text(item.title).font(.caption).lineLimit(1)
            Spacer()
        }
    }
}

private struct ScheduleRowMedium: View {
    let item: WidgetSchedule
    var body: some View {
        HStack(spacing: 8) {
            OwnerDot(kind: item.ownerKind).frame(width: 8, height: 8)
            if let t = item.timeLabel {
                Text(t).font(.caption2).monospacedDigit().foregroundStyle(.secondary)
                    .frame(minWidth: 56, alignment: .leading)
            }
            Text(item.title).font(.subheadline).lineLimit(1)
                .strikethrough(item.isDone && item.isTask)
            Spacer()
        }
    }
}

private struct OwnerDot: View {
    let kind: String
    var body: some View {
        Circle().fill(color)
    }
    private var color: Color {
        switch kind {
        case "me": return .blue
        case "partner": return .pink
        default: return .purple
        }
    }
}

// MARK: - Previews

#Preview("Small", as: .systemSmall) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: WidgetTodayPayload(
        date: "2026-08-28",
        items: [
            WidgetSchedule(id: "1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
            WidgetSchedule(id: "2", title: "장보기", timeLabel: nil, ownerKind: "us", isTask: true, isDone: false),
        ],
    ))
}

#Preview("Medium", as: .systemMedium) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: WidgetTodayPayload(
        date: "2026-08-28",
        items: [
            WidgetSchedule(id: "1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
            WidgetSchedule(id: "2", title: "장보기", timeLabel: nil, ownerKind: "us", isTask: true, isDone: false),
            WidgetSchedule(id: "3", title: "저녁 약속", timeLabel: "오후 7:00", ownerKind: "partner", isTask: false, isDone: false),
        ],
    ))
}

#Preview("Lock Rectangular", as: .accessoryRectangular) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: WidgetTodayPayload(
        date: "2026-08-28",
        items: [
            WidgetSchedule(id: "1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
        ],
    ))
}

#Preview("Lock Circular", as: .accessoryCircular) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: WidgetTodayPayload(
        date: "2026-08-28",
        items: [
            WidgetSchedule(id: "1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
            WidgetSchedule(id: "2", title: "장보기", timeLabel: nil, ownerKind: "us", isTask: true, isDone: false),
            WidgetSchedule(id: "3", title: "저녁 약속", timeLabel: "오후 7:00", ownerKind: "partner", isTask: false, isDone: false),
        ],
    ))
}
