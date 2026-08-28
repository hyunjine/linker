import WidgetKit
import SwiftUI

/// 위젯 번들 진입점. WidgetKit 은 여기서 사용 가능한 위젯 목록을 얻는다.
/// Home Screen (systemSmall/Medium) + Lock Screen (accessoryRectangular/Inline) 모두 제공.
@main
struct LinkerWidgetBundle: WidgetBundle {
    var body: some Widget {
        TodayScheduleWidget()
    }
}
