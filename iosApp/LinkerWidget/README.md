# LinkerWidget — iOS 위젯 (오늘 일정)

홈화면 (`systemSmall` · `systemMedium`) + 잠금화면 (`accessoryRectangular` · `accessoryInline`) 에서
오늘의 스케줄 · 할 일을 보여주는 WidgetKit extension.

## 데이터 흐름

```
Supabase
   │  ( fetch by SchedulesRepository.listInRange(today,today) )
   ▼
KMP shared  ─  TodayWidgetPayloadBuilder.buildJson()  ── JSON String ──┐
                                                                       │
iosApp                                                                 │
  ▸ WidgetSync.refresh()  ── writes JSON to App Group container  ◀─────┘
       (App Group: group.com.hyunjine.linker · file: widget-today.json)
       ▸ WidgetCenter.shared.reloadAllTimelines()

LinkerWidget (extension)
  ▸ SharedTodayStore.read()  ── decodes JSON  ── TimelineEntry
  ▸ TodayScheduleView 가 family (Small / Medium / Lock) 에 맞춰 렌더
```

- 위젯이 Supabase 를 직접 조회하지 않는 이유: 위젯 refresh 마다 auth·네트워크 부담이 크고, App Group 파일 read 는
  즉시·오프라인 대응이 된다.
- 위젯은 자정에 timeline reload 를 요청 (`Timeline(entries:[entry], policy:.after(midnight))`).
- 앱은 foreground 진입 시마다 `WidgetSync.refresh()` 로 payload 갱신.

## Xcode 수동 세팅 (한 번만)

여기 있는 Swift 파일 · Info.plist · entitlements 는 이미 만들어 뒀지만, **Xcode 프로젝트에 target 을 추가하는
작업은 Xcode UI 에서 직접 해야 한다** (provisioning 자동 생성이 CLI 로 안 됨).

### 1. Widget Extension target 추가

1. Xcode 에서 `iosApp/iosApp.xcodeproj` 열기.
2. Project 네비게이터에서 프로젝트 (`iosApp`) 선택 → 좌측 하단 `+` 버튼 → `New Target…`.
3. iOS · `Widget Extension` 선택 → Next.
4. Product Name: `LinkerWidget`, Team: 팀 계정 선택, Bundle Identifier: `com.hyunjine.linker.LinkerWidget`.
5. `Include Live Activity` 체크 해제, `Include Configuration App Intent` 체크 해제.
6. Finish. Xcode 가 `LinkerWidget/` 폴더에 스켈레톤을 만들려고 하는데 — 이미 파일이 있으므로 **덮어쓰지 말고** 취소.

### 2. 이미 있는 파일을 새 target 에 연결

Project 네비게이터에서 `iosApp/LinkerWidget/` 아래의 파일들을 하나씩 선택 → 우측 File Inspector →
`Target Membership` 에서 **LinkerWidget** 만 체크.

대상 파일:
- `LinkerWidgetBundle.swift`
- `TodayScheduleWidget.swift`
- `SharedTodayStore.swift`
- `Info.plist`
- `LinkerWidget.entitlements`

만약 target 생성 시 Xcode 가 자동 생성한 파일이 있으면 삭제 (Remove Reference **OR** Move to Trash).

### 3. App Group 활성화

Apple Developer 콘솔 → Identifiers → App IDs 에서:
- `com.hyunjine.linker` (앱) 과
- `com.hyunjine.linker.LinkerWidget` (위젯)

두 identifier 모두 App Groups capability 를 켜고 `group.com.hyunjine.linker` 를 할당.
(그룹이 없으면 Identifiers → App Groups 에서 먼저 생성.)

Xcode 에서:
- **iosApp target** 선택 → Signing & Capabilities → `+ Capability` → App Groups →
  `group.com.hyunjine.linker` 체크. `iosApp.entitlements` 로 저장되도록.
- **LinkerWidget target** 도 같은 방식으로 App Group 체크. `LinkerWidget.entitlements` 로 저장.

### 4. Entitlements 파일 경로 확인

- `iosApp target` → Build Settings → `Code Signing Entitlements` → `iosApp/iosApp.entitlements`.
- `LinkerWidget target` → Build Settings → `Code Signing Entitlements` → `iosApp/LinkerWidget/LinkerWidget.entitlements`.

### 5. Info.plist 경로 확인

- `LinkerWidget target` → Build Settings → `Info.plist File` → `iosApp/LinkerWidget/Info.plist`.
- `Generate Info.plist File` 는 **NO** (이미 있는 파일 사용).

### 6. Shared 프레임워크 링크 (앱 target 만)

`WidgetSync.swift` 가 shared 의 `TodayWidgetPayloadBuilder` 를 호출하므로 앱 target 은 이미 Shared 프레임워크에
링크돼 있다. 위젯 target 은 Shared 를 링크하지 **않는다** (SDK · 네트워크 부담 · 바이너리 크기 방지).

### 7. 빌드

```
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
가 앱 target 의 `Compile Kotlin Framework` phase 로 자동 호출됨. Xcode 에서 Run → iOS 시뮬레이터.

시뮬레이터에서 위젯 추가:
- 홈화면 길게 누르기 → `+` → `Linker` 검색 → Small / Medium / Lock Rectangular / Lock Inline 중 선택.

## 트러블슈팅

- **`No such module 'Shared'`**: 한 번 이상 빌드해야 shared framework 가 생김. `./gradlew :shared:embedAndSignAppleFrameworkForXcode` 실행.
- **위젯이 계속 "일정 없음"**: App Group ID 가 두 target 에서 다르거나 (오타), 앱이 아직 write 안 한 경우. 앱을 한 번 foreground 로 올렸다 다시 위젯 새로고침.
- **`accessoryRectangular is unavailable in macOS`**: 위젯 target 의 `Supported Destinations` 에서 Mac 을 제외 (iOS 만). 이미 iOS-only 인데 SourceKit 이 경고하면 무시 가능.
- **App Group 파일이 안 보임**: `FileManager.default.containerURL(...)` 가 nil 이면 provisioning 이 최신이 아닌 것. Signing & Capabilities → App Groups 다시 체크 → Xcode 가 provisioning 재발급.
