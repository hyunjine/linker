import WidgetKit
import SwiftUI

// MARK: - Timeline

/// 디데이 위젯 timeline entry. anchor + 다음 milestone 을 payload 에서 읽어 계산 결과를 담는다.
struct DdayEntry: TimelineEntry {
    let date: Date
    /// anchor 부터 오늘까지 경과 일수 (당일=1일째). anchor 미설정이면 nil.
    let daysCount: Int?
    /// anchor "yyyy. MM. dd 부터" 표시 문자열.
    let anchorDisplay: String?
    /// 다음 milestone 라벨 (예: "100일", "1주년"). 없으면 nil.
    let nextLabel: String?
    /// 다음 milestone 도래일 "yyyy. MM. dd".
    let nextDateDisplay: String?
    /// 다음 milestone 까지 D-N 라벨 ("D-3" · "D-DAY").
    let nextDBadge: String?
    /// 커플 us 색 hex (예 "#008AFF"). 카운터 · 배지 컬러에 사용. nil 이면 기본 브랜드 블루.
    let usColorHex: String?
}

struct DdayProvider: TimelineProvider {
    func placeholder(in context: Context) -> DdayEntry {
        DdaySample.filled
    }

    func getSnapshot(in context: Context, completion: @escaping (DdayEntry) -> Void) {
        completion(makeEntry() ?? DdaySample.filled)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<DdayEntry>) -> Void) {
        let now = Date()
        let entry = makeEntry() ?? DdaySample.empty
        // 자정에 카운터 · D-N 갱신 트리거.
        let midnight = Calendar.current.nextDate(
            after: now,
            matching: DateComponents(hour: 0, minute: 0, second: 0),
            matchingPolicy: .nextTime,
        ) ?? now.addingTimeInterval(60 * 60 * 6)
        completion(Timeline(entries: [entry], policy: .after(midnight)))
    }

    /// payload → entry 변환. payload 자체가 없거나 anchor 미설정이면 nil 반환해 caller 가 폴백 처리.
    private func makeEntry() -> DdayEntry? {
        guard let payload = SharedTodayStore.read() else { return nil }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = TimeZone.current
        // payload.date 가 오늘이 아니면 stale — 그래도 anchor · milestone 정보는 여전히 유효할 수 있으니
        // 그대로 사용 (카운터 자체는 오늘 기준으로 다시 계산). 앱 open 이후 refresh 되면 정확 값 반영.
        let today = f.date(from: f.string(from: Date())) ?? Date()

        // Days count (앵커 없으면 nil → 위젯이 폴백 UI 노출).
        let anchorDate = payload.ddayAnchorDate.flatMap(f.date(from:))
        let daysCount: Int? = anchorDate.map { anchor in
            let comps = Calendar.current.dateComponents([.day], from: anchor, to: today)
            return (comps.day ?? 0) + 1 // 당일 = 1일째
        }
        let anchorDisplay = anchorDate.map { formatAnchor($0) }

        // 다음 milestone
        let nextDate = payload.ddayNextMilestoneDate.flatMap(f.date(from:))
        let nextLabel = payload.ddayNextMilestoneLabel
        let nextDisplay = nextDate.map { formatAnchor($0, withSuffix: false) }
        let nextDelta = payload.ddayNextMilestoneDelta
        let nextBadge: String? = nextDelta.map { delta in
            switch delta {
            case 0: return "D-DAY"
            case ..<0: return "D+\(-delta)"
            default: return "D-\(delta)"
            }
        }

        return DdayEntry(
            date: Date(),
            daysCount: daysCount,
            anchorDisplay: anchorDisplay,
            nextLabel: nextLabel,
            nextDateDisplay: nextDisplay,
            nextDBadge: nextBadge,
            usColorHex: payload.usColorHex,
        )
    }
}

private func formatAnchor(_ date: Date, withSuffix: Bool = true) -> String {
    let f = DateFormatter(); f.dateFormat = "yyyy. MM. dd"; f.timeZone = TimeZone.current
    let s = f.string(from: date)
    return withSuffix ? "\(s) 부터" : s
}

// MARK: - Widget definitions

/// 홈 스크린 D-day 위젯. systemSmall (2×2) + systemMedium (4×2) 두 가지 지원.
struct DdayHomeWidget: Widget {
    let kind: String = "DdayHomeWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: DdayProvider()) { entry in
            DdayHomeView(entry: entry)
                .containerBackground(for: .widget) { Color.white }
        }
        .configurationDisplayName("디데이")
        .description("함께한 지, 다음 기념일까지.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

/// 잠금화면 accessoryCircular D-day 위젯. iOS 가 자동으로 mono tint 처리.
struct DdayLockWidget: Widget {
    let kind: String = "DdayLockWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: DdayProvider()) { entry in
            DdayLockView(entry: entry)
                .containerBackground(for: .widget) { Color.clear }
        }
        .configurationDisplayName("디데이")
        .description("잠금화면에서 함께한 지.")
        .supportedFamilies([.accessoryCircular])
    }
}

// MARK: - Views

private let brandBlue = Color(red: 0.0, green: 0.5412, blue: 1.0)
private let textPrimary = Color(red: 0.1020, green: 0.1020, blue: 0.1020)
private let textSecondary = Color(red: 0.4196, green: 0.4196, blue: 0.4392)
private let badgeBg = Color(red: 0.9020, green: 0.9490, blue: 1.0)
private let divider = Color(red: 0.9059, green: 0.9059, blue: 0.9137)

/// payload hex ("#RRGGBB") → SwiftUI Color. 잘못된 값이면 브랜드 블루로 폴백.
private func colorFromHex(_ hex: String?) -> Color {
    guard let hex = hex, hex.hasPrefix("#"), hex.count == 7,
          let val = UInt32(hex.dropFirst(), radix: 16) else { return brandBlue }
    let r = Double((val >> 16) & 0xff) / 255.0
    let g = Double((val >> 8) & 0xff) / 255.0
    let b = Double(val & 0xff) / 255.0
    return Color(red: r, green: g, blue: b)
}

private struct DdayHomeView: View {
    @Environment(\.widgetFamily) private var family
    let entry: DdayEntry

    var body: some View {
        switch family {
        case .systemSmall: DdaySmallView(entry: entry)
        case .systemMedium: DdayMediumView(entry: entry)
        default: DdaySmallView(entry: entry)
        }
    }
}

/// systemSmall (Figma 4204:78284). 좌상 라벨 · 큰 카운터 · 하단 앵커 날짜.
private struct DdaySmallView: View {
    let entry: DdayEntry
    var body: some View {
        let brand = colorFromHex(entry.usColorHex)
        VStack(alignment: .leading, spacing: 0) {
            Text("💙 함께한 지")
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(brand)
            Spacer(minLength: 0)
            HStack(alignment: .lastTextBaseline, spacing: 3) {
                Text(entry.daysCount.map { "\($0)" } ?? "-")
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(brand)
                Text("일")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(brand)
            }
            Spacer(minLength: 0)
            Text(entry.anchorDisplay ?? "앵커를 설정해주세요")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(textSecondary)
        }
    }
}

/// systemMedium (Figma 4204:78289). 좌측 카운터 · 세로 divider · 우측 다음 milestone.
private struct DdayMediumView: View {
    let entry: DdayEntry
    var body: some View {
        let brand = colorFromHex(entry.usColorHex)
        HStack(alignment: .top, spacing: 0) {
            // 좌 절반
            VStack(alignment: .leading, spacing: 0) {
                Text("💙 함께한 지")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(brand)
                Spacer(minLength: 0)
                HStack(alignment: .lastTextBaseline, spacing: 4) {
                    Text(entry.daysCount.map { "\($0)" } ?? "-")
                        .font(.system(size: 56, weight: .bold))
                        .foregroundColor(brand)
                    Text("일")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(brand)
                }
                Spacer(minLength: 0)
                Text(entry.anchorDisplay ?? "앵커를 설정해주세요")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Divider().overlay(divider).frame(width: 1)
                .padding(.vertical, 4)

            // 우 절반
            VStack(alignment: .leading, spacing: 0) {
                Text("🎉 다음 기념일")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(textSecondary)
                Spacer(minLength: 0).frame(height: 8)
                Text(entry.nextLabel ?? "—")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(textPrimary)
                Text(entry.nextDateDisplay ?? " ")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(textSecondary)
                    .padding(.top, 4)
                Spacer(minLength: 0)
                HStack {
                    Spacer(minLength: 0)
                    if let badge = entry.nextDBadge {
                        Text(badge)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(brand)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(brand.opacity(0.12))
                            )
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, 20)
        }
    }
}

/// accessoryCircular (Figma 4204:78301). 시스템이 mono tint 처리하므로 컬러는 시스템 위임.
private struct DdayLockView: View {
    let entry: DdayEntry
    var body: some View {
        VStack(spacing: 0) {
            Text("D+")
                .font(.system(size: 10, weight: .bold))
                .kerning(0.4)
            Text(entry.daysCount.map { "\($0)" } ?? "-")
                .font(.system(size: 26, weight: .bold))
                .minimumScaleFactor(0.5)
                .lineLimit(1)
        }
        .foregroundStyle(.white)
        .padding(6)
    }
}

// MARK: - Sample data

private enum DdaySample {
    static let filled = DdayEntry(
        date: Date(),
        daysCount: 97,
        anchorDisplay: "2025. 03. 22 부터",
        nextLabel: "100일",
        nextDateDisplay: "2026. 06. 29",
        nextDBadge: "D-3",
        usColorHex: "#008AFF",
    )
    static let empty = DdayEntry(
        date: Date(),
        daysCount: nil,
        anchorDisplay: nil,
        nextLabel: nil,
        nextDateDisplay: nil,
        nextDBadge: nil,
        usColorHex: "#008AFF",
    )
}
