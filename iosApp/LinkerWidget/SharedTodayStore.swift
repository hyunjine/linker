import Foundation

/// 위젯이 읽는 오늘 일정 payload. 메인 앱이 App Group 컨테이너에 JSON 으로 write,
/// 위젯이 read. Supabase 를 위젯에서 직접 조회하지 않는 이유:
///  - 위젯 refresh 마다 auth · 네트워크 부담
///  - App Group 파일 read 는 즉시 · 오프라인 대응
struct WidgetSchedule: Codable, Identifiable, Hashable {
    let id: String
    let title: String
    /// "오전 10:00" 같이 이미 포맷된 문자열. 종일 · 할 일은 nil.
    let timeLabel: String?
    /// "me" · "partner" · "us". 위젯이 색 매핑.
    let ownerKind: String
    /// true 면 체크박스, false 면 원 마커. task 여부.
    let isTask: Bool
    let isDone: Bool
}

struct WidgetTodayPayload: Codable {
    /// 오늘 날짜 (ISO yyyy-MM-dd). 위젯이 이 날짜 기준 헤더 표시.
    let date: String
    /// 오늘의 스케줄/할 일. 시각 오름차순 (nil 은 뒤).
    let items: [WidgetSchedule]
}

/// App Group 을 통해 앱 ↔ 위젯 데이터 브릿지.
/// 여기서 group ID 는 Apple Developer 콘솔 · 두 target 의 Signing & Capabilities 에 등록돼야 함.
enum SharedTodayStore {
    static let appGroupId = "group.com.hyunjine.linker"
    private static let fileName = "widget-today.json"

    private static var containerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId)
    }

    static func read() -> WidgetTodayPayload? {
        guard let url = containerURL?.appendingPathComponent(fileName),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(WidgetTodayPayload.self, from: data)
    }

    /// 앱 쪽에서 호출. 새 payload 를 컨테이너에 write 하고 위젯을 refresh 하도록 유도한다.
    /// (WidgetCenter reload 는 위젯 프로세스에서만 유효하므로 앱은 파일 갱신만 하고
    /// WidgetKit 에게는 [WidgetCenter.shared.reloadAllTimelines()] 를 별도 호출.)
    static func write(_ payload: WidgetTodayPayload) {
        guard let url = containerURL?.appendingPathComponent(fileName) else { return }
        if let data = try? JSONEncoder().encode(payload) {
            try? data.write(to: url, options: .atomic)
        }
    }
}
