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
        let payload = validatedPayload() ?? samplePayload
        completion(TodayScheduleEntry(date: Date(), payload: payload))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayScheduleEntry>) -> Void) {
        let now = Date()
        // 자정을 넘어가면 App Group 파일이 아직 어제 payload 인 상태 (앱이 refresh 를
        // 아직 못 돌린 경우). 어제 데이터를 그대로 뿌리면 사용자에게 오해를 줌.
        // payload.date 가 오늘과 다르면 nil 로 대체 → 위젯은 "일정 없음" 표시.
        let payload = validatedPayload()
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

    /// App Group 파일에서 payload 를 읽되, `date` 가 오늘이 아니면 nil 반환.
    /// 자정 넘어가서 앱이 아직 refresh 못 돌린 경우 어제 items 를 그대로 노출하는 걸 방지.
    private func validatedPayload() -> WidgetTodayPayload? {
        guard let payload = SharedTodayStore.read() else { return nil }
        let today = todayIsoString()
        return payload.date == today ? payload : nil
    }

    private func todayIsoString() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = TimeZone.current
        return f.string(from: Date())
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
                // 홈화면 위젯은 반투명 material 로 벽지가 은은히 비치도록 (이슈 #174).
                // Lock screen accessory 는 iOS 가 자체 시스템 톤을 씌우므로 아래 배경은
                // 사실상 무시됨 (AccessoryWidgetBackground 별도 사용).
                .containerBackground(for: .widget) {
                    Rectangle().fill(.ultraThinMaterial)
                }
        }
        .configurationDisplayName("오늘 일정")
        .description("현진이랑민교의 오늘 스케줄과 할 일을 한눈에.")
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
                AdaptiveScheduleList(items: items, rowSize: .small)
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
                AdaptiveScheduleList(items: items, rowSize: .medium)
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

/// 위젯 높이가 허용하는 만큼 일정 row 를 채우고, 넘치는 항목만 `+N개` 로 표시하는 리스트.
///
/// `ViewThatFits` 는 자식들을 순서대로 시도해 부모가 제안한 space 에 맞는 첫 view 를 렌더한다.
/// - 첫 시도: 전체 항목 노출 · 오버플로 없음
/// - 이후: 마지막 항목부터 하나씩 숨기고 대신 `+숨긴수개` 표시
/// - 최소 1개 + `+N개` 는 항상 fit 가정 (실제 안 되면 SwiftUI 가 마지막 것 선택)
///
/// 항목 수 상한은 실무 상 payload 개수 (`TodayWidgetPayload` 가 이미 제한) — 여기선 방어적으로
/// 최대 10 candidate 를 만든다 (11개 이상은 첫 후보로 자동 통과 후 iOS 가 clip).
private struct AdaptiveScheduleList: View {
    let items: [WidgetSchedule]
    let rowSize: RowSize

    enum RowSize { case small, medium }

    var body: some View {
        ViewThatFits(in: .vertical) {
            ForEach(candidateHiddenCounts, id: \.self) { hiddenCount in
                content(hiddenCount: hiddenCount)
            }
        }
    }

    /// 시도할 "숨긴 항목 수" 후보 리스트. 0 부터 items.count-1 까지 오름차순 → 오버플로 최소가
    /// 우선. `ViewThatFits` 가 앞에서부터 fit 되는 첫 후보를 채택.
    private var candidateHiddenCounts: [Int] {
        Array(0..<items.count)
    }

    @ViewBuilder
    private func content(hiddenCount: Int) -> some View {
        let shownCount = items.count - hiddenCount
        VStack(alignment: .leading, spacing: rowSize == .small ? 4 : 6) {
            ForEach(items.prefix(shownCount)) { item in
                switch rowSize {
                case .small: ScheduleRowSmall(item: item)
                case .medium: ScheduleRowMedium(item: item)
                }
            }
            if hiddenCount > 0 {
                Text("+\(hiddenCount)개")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
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

// 프리뷰 샘플 payload. 모든 프리뷰가 재사용하도록 helper 로 뽑음.
private func samplePayloadFilled() -> WidgetTodayPayload {
    WidgetTodayPayload(
        date: dateStringToday(),
        items: [
            WidgetSchedule(id: "1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
            WidgetSchedule(id: "2", title: "장보기", timeLabel: nil, ownerKind: "us", isTask: true, isDone: false),
            WidgetSchedule(id: "3", title: "저녁 약속", timeLabel: "오후 7:00", ownerKind: "partner", isTask: false, isDone: false),
        ],
    )
}

private func samplePayloadMany() -> WidgetTodayPayload {
    WidgetTodayPayload(
        date: dateStringToday(),
        items: [
            WidgetSchedule(id: "1", title: "병원 예약", timeLabel: "오전 10:00", ownerKind: "me", isTask: false, isDone: false),
            WidgetSchedule(id: "2", title: "장보기", timeLabel: nil, ownerKind: "us", isTask: true, isDone: false),
            WidgetSchedule(id: "3", title: "점심 미팅", timeLabel: "오후 12:30", ownerKind: "me", isTask: false, isDone: false),
            WidgetSchedule(id: "4", title: "저녁 약속", timeLabel: "오후 7:00", ownerKind: "partner", isTask: false, isDone: false),
            WidgetSchedule(id: "5", title: "택배 픽업", timeLabel: nil, ownerKind: "me", isTask: true, isDone: true),
            WidgetSchedule(id: "6", title: "운동", timeLabel: "오후 9:00", ownerKind: "me", isTask: false, isDone: false),
        ],
    )
}

private func samplePayloadEmpty() -> WidgetTodayPayload {
    WidgetTodayPayload(date: dateStringToday(), items: [])
}

private func dateStringToday() -> String {
    let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f.string(from: Date())
}

// ── 홈화면 위젯 ──

#Preview("Home / Small · 일정 있음", as: .systemSmall) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: samplePayloadFilled())
}

#Preview("Home / Small · 빈 상태", as: .systemSmall) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: samplePayloadEmpty())
}

#Preview("Home / Medium · 일정 있음", as: .systemMedium) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: samplePayloadFilled())
}

#Preview("Home / Medium · +N개 오버플로", as: .systemMedium) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: samplePayloadMany())
}

#Preview("Home / Medium · 빈 상태", as: .systemMedium) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: samplePayloadEmpty())
}

// ── 잠금화면 액세서리 ──

#Preview("Lock / Rectangular", as: .accessoryRectangular) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: samplePayloadFilled())
}

#Preview("Lock / Rectangular · 빈 상태", as: .accessoryRectangular) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: samplePayloadEmpty())
}

#Preview("Lock / Circular", as: .accessoryCircular) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: samplePayloadFilled())
}

#Preview("Lock / Inline", as: .accessoryInline) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: samplePayloadFilled())
}

#Preview("Lock / Inline · 빈 상태", as: .accessoryInline) {
    TodayScheduleWidget()
} timeline: {
    TodayScheduleEntry(date: Date(), payload: samplePayloadEmpty())
}
