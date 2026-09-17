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

/// 4×2 split 위젯 (#244) 우측 컬럼에 나열되는 미완료 할 일.
/// `startDate` 가 오늘보다 과거이면 위젯이 "지연" 뱃지로 강조 표시.
struct WidgetOpenTask: Codable, Identifiable, Hashable {
    let id: String
    let title: String
    /// "yyyy-MM-dd" — overdue 판정용 (오늘보다 과거면 지연).
    let startDate: String
    /// "me" · "partner" · "us" — 좌측 owner dot 색.
    let ownerKind: String
}

struct WidgetTodayPayload: Codable {
    /// 오늘 날짜 (ISO yyyy-MM-dd). 위젯이 이 날짜 기준 헤더 표시.
    let date: String
    /// 오늘의 스케줄/할 일. 시각 오름차순 (nil 은 뒤).
    let items: [WidgetSchedule]
    /// 뷰어 관점 소유자별 색상 hex ("#RRGGBB"). `OwnerDot` 이 `ownerKind` 로 셋 중 하나 선택.
    /// 앱 프로필 `users.calendar_color` 반영. Kotlin 이 최신값을 이 payload 로 실어 보냄 →
    /// 사용자가 앱에서 색을 바꾸면 다음 refresh 부터 위젯 색도 즉시 갱신.
    /// 구버전 payload 호환 위해 optional — nil 이면 위젯이 시스템 fallback 컬러 사용.
    let meColorHex: String?
    let partnerColorHex: String?
    let usColorHex: String?
    /// 4×2 split 위젯 (#244) 우측 컬럼 — 오늘까지의 미완료 할 일 (누적).
    /// 구버전 payload 호환 위해 optional. 기존 today 위젯은 이 필드를 무시.
    let openTasks: [WidgetOpenTask]?
    /// 캘린더 위젯 미니 달력용 — 이번 달 각 날짜별 이벤트 owner 리스트.
    /// key = "yyyy-MM-dd", value = ["me", "partner", …] (중복 제거). 이벤트 없는 날은 map 에 없음.
    /// 구버전 payload 호환 위해 optional.
    let monthEvents: [String: [String]]?

    /// 프리뷰 · 샘플 payload 편의를 위한 default init. 컬러 hex · openTasks · monthEvents 는 선택.
    init(
        date: String,
        items: [WidgetSchedule],
        meColorHex: String? = nil,
        partnerColorHex: String? = nil,
        usColorHex: String? = nil,
        openTasks: [WidgetOpenTask]? = nil,
        monthEvents: [String: [String]]? = nil,
    ) {
        self.date = date
        self.items = items
        self.meColorHex = meColorHex
        self.partnerColorHex = partnerColorHex
        self.usColorHex = usColorHex
        self.openTasks = openTasks
        self.monthEvents = monthEvents
    }
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
