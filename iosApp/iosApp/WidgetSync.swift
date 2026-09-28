import Foundation
import WidgetKit
import Shared

/// 앱 → 위젯 데이터 동기화. shared 의 [TodayWidgetPayloadBuilder] 로 JSON 을 만들어
/// App Group 컨테이너에 write 하고 WidgetKit timeline reload.
///
/// - App Group ID 는 위젯 target 의 SharedTodayStore 와 동일해야 함
///   (Signing & Capabilities · App Groups 에서 두 target 다 체크되어 있어야 파일 공유 가능).
/// - 로그아웃 · 커플 미가입이면 shared 가 빈 items 로 payload 를 만들어 반환 — 위젯은 "일정 없음" 표시.
/// - 세션 복원 중 · 토큰 갱신 실패 · 일정 조회 실패면 shared 가 nil 을 반환 → 파일을 덮어쓰지 않고
///   기존 위젯 데이터를 유지한다 (#367). 다음 foreground · 변경 시점에 다시 갱신.
enum WidgetSync {
    private static let appGroupId = "group.com.hyunjine.linker"
    private static let fileName = "widget-today.json"

    /// 로그인 직후 · 앱 foreground · 스케줄 변경 후 등 데이터가 바뀔 만한 시점에 호출.
    /// KMP suspend fun 은 Swift 에서 completion handler 로 자동 노출됨.
    /// [completion] 은 새 데이터를 썼는지 알리는 optional 콜백 — silent push 등에서 완료 시점을 알아야 할 때 사용.
    /// 건너뛴 경우 (nil) · 실패는 false.
    static func refresh(completion: ((Bool) -> Void)? = nil) {
        TodayWidgetPayloadBuilder.shared.buildJson { json, error in
            if let error = error {
                print("[WidgetSync] shared buildJson failed: \(error)")
                completion?(false)
                return
            }
            guard let json = json else {
                print("[WidgetSync] 세션 미준비 또는 조회 실패 — 기존 위젯 유지")
                completion?(false)
                return
            }
            write(json)
            WidgetCenter.shared.reloadAllTimelines()
            completion?(true)
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
