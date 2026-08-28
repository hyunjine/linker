import Foundation
import WidgetKit
import Shared

/// 앱 → 위젯 데이터 동기화. shared 의 [TodayWidgetPayloadBuilder] 로 JSON 을 만들어
/// App Group 컨테이너에 write 하고 WidgetKit timeline reload.
///
/// - App Group ID 는 위젯 target 의 SharedTodayStore 와 동일해야 함
///   (Signing & Capabilities · App Groups 에서 두 target 다 체크되어 있어야 파일 공유 가능).
/// - Supabase 세션이 없거나 커플 미가입이면 shared 가 빈 items 로 payload 를 만들어 반환 —
///   위젯은 그대로 "일정 없음" 표시.
enum WidgetSync {
    private static let appGroupId = "group.com.hyunjine.linker"
    private static let fileName = "widget-today.json"

    /// 로그인 직후 · 앱 foreground · 스케줄 변경 후 등 데이터가 바뀔 만한 시점에 호출.
    /// KMP suspend fun 은 Swift 에서 completion handler 로 자동 노출됨.
    static func refresh() {
        TodayWidgetPayloadBuilder.shared.buildJson { json, error in
            if let error = error {
                print("[WidgetSync] shared buildJson failed: \(error)")
                return
            }
            guard let json = json else { return }
            write(json)
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    private static func write(_ json: String) {
        guard let container = FileManager.default
                .containerURL(forSecurityApplicationGroupIdentifier: appGroupId),
              let data = json.data(using: .utf8)
        else { return }
        let url = container.appendingPathComponent(fileName)
        try? data.write(to: url, options: .atomic)
    }
}
