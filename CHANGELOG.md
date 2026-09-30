# Changelog

이 프로젝트의 모든 릴리즈 노트를 여기에 기록한다. 포맷은
[Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 를 따르고,
버전은 [Semantic Versioning](https://semver.org/lang/ko/) 을 따른다.

버전 소스는 `iosApp/Configuration/Config.xcconfig` 의 `MARKETING_VERSION`
이며, 이 파일의 최상단 (Unreleased 제외한 첫 `## [X.Y.Z]` 섹션) 이 GitHub
Release 노트로 자동 게시된다 (`.github/workflows/release.yml`).

## [Unreleased]

## [1.6.1] - 2026-09-30

### 개선
- 화면을 불러오는 동안 빙글빙글 도는 표시 대신 실제 화면 모양의 로딩 화면이 보여요. 디데이 · 릴리즈 노트 · 에브리타임 시간표 · 할 일 목록에 적용했어요.

### 버그 수정
- 공동 캘린더 색을 바꿔도 상대방 화면 · 위젯에 반영되지 않던 문제 해결. 이제 커플이 같은 색을 함께 써요.
- 디데이 화면을 스크롤할 때 하단 영역이 잘려 보이던 문제 해결.

## [1.6.1 · 개발자 노트]

> 이 섹션은 GitHub Release 본문에는 함께 게시되지만, 앱의 릴리즈 노트 화면에서는 렌더링 되지 않는다.

### 개선
- 로딩 스켈레톤 (#398 · #399 · #400 · #401) — 디데이 · 릴리즈 노트 · 에브리타임 · 할 일 목록의 `CircularProgressIndicator` 를 각 화면 배치의 스켈레톤으로 교체. 공용 `designsystem/common/Skeleton.kt` (`SkeletonBar` · `SkeletonBox`), 에브리타임은 `TimetableCard(skeletonSlots)`.
- 스켈레톤 최소 노출 0.8초 (#406) — `withSkeletonMinDuration`. 스켈레톤이 보이는 로딩에만 적용 (재진입 · silent 갱신 · 학기 전환 제외).
- 스켈레톤 shimmer (#408) — 창 좌표 · 프레임 시각 기준으로 모든 블록의 빛 띠가 동기화. draw 단계에서만 상태를 읽어 recomposition 없음.

### 버그 수정
- 공동 캘린더 색 커플 단위 동기화 (#392) — `couples.us_calendar_color` + RPC `set_couple_us_calendar_color` + `couple_members` INSERT 트리거로 신규 커플 색 채우기. Migration `20260929000000__couples_us_calendar_color` 운영 적용 완료. `users.us_calendar_color` 는 호환용으로 유지 (RPC 가 두 멤버 값도 함께 갱신).
- 디데이 스크롤 시 하단 바텀바 영역 잘림 (#391).

### 인프라 · 빌드
- 배포 완료 알림 자동 발송 제거 (#380). Xcode Cloud `iosApp/ci_scripts/ci_post_xcodebuild.sh` 가 archive 직후 (TestFlight 업로드 · 스토어 출시 전) 알림을 보내던 문제 → 스크립트 삭제. 스토어 출시 확인 후 Actions `Release Broadcast` (`.github/workflows/release-broadcast.yml`, workflow_dispatch) 로 수동 발송.
- 배포 완료 알림을 App Store Connect 웹훅으로 자동 발송 (#395). TestFlight 빌드 처리 완료 (`BUILD_UPLOAD_STATE_UPDATED` → `COMPLETE`) 시 Edge Function `asc-webhook` 이 `buildUploads` 로 버전을 조회해 `broadcast-release-note` 호출. 수동 발송 워크플로 (#380) 는 폴백으로 유지.
- `supabase-deploy.yml` Supabase CLI 버전 고정 (`latest` 조회 rate limit 로 배포 실패).
- App Store Connect 웹훅 등록 (#395) — 이벤트 `BUILD_UPLOAD_STATE_UPDATED`, url `…/functions/v1/asc-webhook`. `ASC_WEBHOOK_SECRET` 재발급 · 반영, `webhookPings` 로 서명 검증 · 200 응답 확인. 1.6.1 이 웹훅 자동 발송의 첫 실전.

## [1.6.0] - 2026-09-28

### 신규 기능
- **더보기** — 드로워 아래 "더보기" 탭에서 릴리즈 노트 · 현재 버전 · 최신 버전을 확인하고 로그아웃할 수 있어요. 새 버전이 나오면 알려줘요.
- **새 앱 아이콘** — 앱 로고가 바뀌었어요.

### 개선
- 위젯에는 내 할 일과 공동 할 일만 보여요. 상대방 할 일은 숨기고, 상대방 일정은 그대로 보여요.
- 홈 화면 위젯에서도 할 일이 일정보다 먼저 보여요.
- 드로워를 정리했어요. "할 일 목록" 카드가 생기고, 디데이 · 에브리타임 아이콘이 바뀌었어요.
- 로그아웃할 때 한 번 더 확인해요.
- 커플 관리 화면에서 연결 해제 버튼을 뺐어요.

## [1.6.0 · 개발자 노트]

> 이 섹션은 GitHub Release 본문에는 함께 게시되지만, 앱의 릴리즈 노트 화면에서는 렌더링 되지 않는다.

### 신규 기능
- 더보기 화면 (#385) — `feature/more` (`MoreRoute` · `MoreScreen` · `MoreViewModel`). 현재 버전은 `Config.xcconfig` `MARKETING_VERSION` 을 `Secrets.AppVersion` 으로 생성, 최신 버전은 GitHub Releases latest 조회 후 `compareVersions` 로 비교. 로그아웃은 stacked `AppAlertDialog` 확인 후 실행.
- 앱 로고 교체 (#387) — iOS `app-icon-1024.png` (RGB), Android 런처 아이콘 전 해상도 · adaptive 배경 `#FFFFFF`, 앱 내 `ic_app_logo.png`.

### 리팩터
- 드로워 (#385) — 릴리즈 노트 · 로그아웃을 더보기로 이동, `TasksCard` ("할 일 목록"), 하단 탭 디데이 · 에브리타임 · 더보기. 할 일 화면 타이틀도 "할 일 목록".
- 위젯 payload (#382) — `isPartnerTask` 로 상대방 할 일 제외 (상대방 일정은 유지), `WidgetItemOrder` 로 잠금 · 홈 위젯 모두 할 일 우선.
- 커플 관리 (#381) — 연결 해제 버튼 UI 만 제거 (연결 해제 로직 · RPC 는 유지).

## [1.5.1] - 2026-09-28

### 버그 수정
- 새 버전이 출시돼도 업데이트 알림이 오지 않던 문제 해결.

## [1.5.1 · 개발자 노트]

> 이 섹션은 GitHub Release 본문에는 함께 게시되지만, 앱의 릴리즈 노트 화면에서는 렌더링 되지 않는다.

### 인프라 · 빌드
- Xcode Cloud post-build 스크립트를 `ci_scripts/` → `iosApp/ci_scripts/` 로 이동 (#372). Xcode Cloud 는 `ci_scripts` 를 `.xcodeproj` 와 같은 디렉터리에서만 찾아, 루트에 있던 `ci_post_xcodebuild.sh` 가 실행되지 않았음 → v1.5.0 배포 완료 알림 누락. 1.5.1 archive 부터 broadcast-release-note 가 호출된다.
- Xcode Cloud 네트워크 일시 오류 대비 (#376). 1.5.1 첫 archive (빌드 8) 가 Gradle 의 kotlin-compiler 다운로드 중 github.com 연결 시간 초과로 실패 → Gradle 다운로드 재시도 · 시간 제한 확대, Release archive Gradle 실패 시 1회 재시도. 배포 알림은 `CI_XCODEBUILD_EXIT_CODE == 0` 일 때만 발송하고, 알림 실패는 빌드를 실패시키지 않음. 빌드 번호 9 로 재출시.

## [1.5.0] - 2026-09-28

### 신규 기능
- **할 일 내역** — 드로워 "할 일"에서 남은 일 · 끝낸 일을 한눈에 보고, 최신순 · 과거순으로 정렬하거나 바로 체크 · 편집할 수 있어요. 내 할 일과 공동 할 일만 모아 보여줘요.
- **알림 내역** — 메인 상단 종 아이콘에서 최근 30일 동안 받은 알림 (상대방 일정 변경 · 시작 알림 · 공지 · 업데이트) 을 날짜별로 다시 볼 수 있어요. 아래로 당기면 새로고침돼요.
- **공동 캘린더 커스텀 색** — 커플 관리에서 기본 색 외에 원하는 색을 직접 골라 공동 캘린더에 쓸 수 있어요.

### 개선
- 드로워 상단을 새로 디자인했어요. 나와 상대방 프로필이 함께 보이고, "내 프로필" · "커플 설정" 버튼으로 바로 이동해요.
- 커플 관리 화면을 정리했어요. 연결 해제는 화면 아래로 옮기고, 한 번 더 확인하도록 했어요.
- 드로워의 할 일 · 릴리즈 노트 · 로그아웃을 누를 때 터치 효과가 보여요.

### 버그 수정
- 위젯에 할 일이 있는데 가끔 비어 보이던 문제 해결.
- 일정 상세 시트에서 시간 있는 일정이 시작 시간 순으로 정렬되지 않던 문제 해결.

## [1.5.0 · 개발자 노트]

> 이 섹션은 GitHub Release 본문에는 함께 게시되지만, 앱의 릴리즈 노트 화면에서는 렌더링 되지 않는다.

### 신규 기능
- 할 일 내역 (#304) — `feature/task` (`TasksRoute` · `TasksScreen` · `TasksViewModel`). 남은 일은 `listOpenTasks(today)` (위젯과 동일 기준), 끝낸 일은 신규 `listDoneTasks()`. 정렬 메뉴는 Popup 대신 화면 내 오버레이로 바깥 1회 탭에 닫힘, 정렬 변경 시 `requestScrollToItem` 으로 인덱스 유지.
- 알림 내역 (#303 · #365) — `feature/notification`. 기획서 `NOTIFICATION_HISTORY.md`. 당겨서 새로고침은 `PullToRefreshBox`, 인디케이터 최소 500ms.
- 커플 관리 커스텀 색 (#335) — `CustomColorSwatch` 를 `designsystem/common` 으로 분리해 프로필 편집 · 커플 관리 공용.

### 리팩터
- 드로워 상단 `CoupleProfileHeader` (#335) — 기존 `ProfileHeader` · `CoupleLinkRow` 대체. `MainUiState.partnerProfile` 추가.
- `AppAlertDialog` 에 `stacked` 옵션 · `AlertActionStyle.DestructiveText` 추가 (기본값 유지).
- 위젯 payload (#367) — `TodayWidgetPayloadBuilder.buildJson()` 이 `String?` 반환. 세션이 로그인/로그아웃으로 확정될 때까지 최대 15초 대기, 곧 만료될 토큰은 선갱신, 갱신 · 일정 조회 실패 시 `null` 로 위젯 파일 유지. silent push 건너뜀은 `.noData` 응답.

### DB · 백엔드
- Migration `20260923000000__notifications_history` — `public.notifications` (본인 row 만 SELECT RLS, 쓰기는 service role), 30일 지난 내역 매일 정리 pg_cron `purge-old-notifications-daily`. 운영 적용 완료.
- Edge Functions `send-schedule-push` · `send-announcement` · `broadcast-release-note` — 발송 전 수신자별 내역 기록 (`recordNotifications`, `dedupe_key` 로 재시도 중복 차단).

### 버그 수정
- 일정 상세 시트 timed 일정 정렬을 raw `HH:MM:SS` 기준으로 (#360).

### 인프라 · 빌드
- 배포 완료 push 를 release 워크플로에서 Xcode Cloud `ci_post_xcodebuild.sh` (archive 성공 후) 로 이관 (#356).

## [1.4.1] - 2026-09-22

### 버그 수정
- 잠금화면 · 오늘일정 위젯에서 이미 완료한 할 일이 계속 남아 보이던 문제 해결. 체크한 순간 위젯 리스트에서도 사라져요.

## [1.4.1 · 개발자 노트]

> 이 섹션은 GitHub Release 본문에는 함께 게시되지만, 앱의 릴리즈 노트 화면에서는 렌더링 되지 않는다.

### 버그 수정 (#351)
- `TodayWidgetPayloadBuilder.buildPayload` 가 오늘 rows → items 변환 앞단에서 `type='task' AND isDone` row 를 제외하도록 수정. 홈 SplitWidget 우측은 별도 `openTasks` (백엔드 `is_done=false` 필터) 를 써서 정상 동작했지만, 잠금 · 오늘일정 위젯은 `items` 를 그대로 렌더해 완료된 할 일이 남아있었음. Calendar/Split 은 이미 task 자체를 filter out 해 영향 없음.

### 인프라 · 빌드
- `.github/workflows/supabase-deploy.yml` 신설 (#348). dev push 시 `supabase/functions/**` 변경분만 감지해 Supabase CLI 로 자동 배포. workflow_dispatch 로 전체 함수 강제 재배포도 지원. v1.4.0 release 자동화가 broadcast-release-note 404 로 실패한 근본 원인 (함수 · 마이그레이션 수동 배포 정책이라 프로덕션에 못 올라감) 을 프로세스 레벨에서 방지.

## [1.4.0] - 2026-09-22

### 신규 기능
- **디데이(D-day)** — 함께한 지 N일 · 다음 기념일 D-N 을 한 화면에서 확인해요. 홈 위젯 (2×2 · 4×2) 과 잠금화면 위젯도 추가돼 한눈에 볼 수 있어요.
- **에브리타임 시간표** — 프로필에 에브리타임 공유 URL 을 등록하면 파트너와 서로의 시간표를 확인할 수 있어요. 본인/상대방 세그먼트로 스위칭 · 학기 변경 지원.
- **생일 자동 등록** — 프로필에 생일을 넣어두면 캘린더에 자동으로 매년 등록되고, 커플이면 상대방 캘린더에도 함께 보여요.
- **공동 캘린더 색상** — 커플 연결 화면에서 "우리" 캘린더에 쓸 색을 직접 골라 상대방 · 나 색과 구분할 수 있어요.
- **캘린더 위젯 컬러** — 토요일 · 일요일 · 공휴일이 자체 색으로 표시돼요.

### 개선
- 드로워 UI 재배치 — 상대방 연결 카드 정리, 기념일 · 에브리타임 진입을 하단 고정 액션바로.
- 프로필 편집 · 에브리타임 화면에서 스크롤 시 상단 앱바가 배경과 자연스럽게 이어지도록 정리.
- 일정 상세 시트에서 시스템이 자동 관리하는 로우 (생일 · 디데이 기념일) 는 편집 진입을 차단해 실수로 지워지지 않아요.

### 버그 수정
- 프로필 편집에서 저장을 눌러도 저장이 안 되고 이전 화면으로 이동도 안 되던 문제 해결.
- 프로필에서 닉네임 · 생일을 바꾸면 파트너 폰에 관련 알림이 수십 개 폭탄으로 가던 문제 해결 (자동 관리 로우는 알림 대상에서 제외).
- 공동 색을 변경했다가 저장에 실패해도 UI 에 잘못된 색이 남던 문제 해결.
- 파트너에게 개인 일정 알림이 잘못 전달되던 스케줄 알림 정책 재정의.
- 시간표 요일 헤더 하단 줄이 시각 라벨을 가리던 문제 해결.

## [1.4.0 · 개발자 노트]

> 이 섹션은 GitHub Release 본문에는 함께 게시되지만, 앱의 릴리즈 노트 화면에서는 렌더링 되지 않는다.

### 리팩터
- `FrostedTopBar` 를 `feature/dday` → `designsystem/common` 으로 이동해 D-day · 에브리타임 공용화.
- `EverytimeUrlSheet` 을 독립 파일로 분리 (프로필 편집에서 URL 필드 제거 후 empty state 의 CTA 가 유일 진입점).
- `EverytimeTimetableViewModel` — 본인 · 파트너 tab payload 분리, 첫 진입 시 등록된 유저 탭으로 auto-select.
- `SchedulesRepository.Row` 에 `birthday_uid` · `source` 필드 추가.

### DB · 백엔드
- Migrations 11 건:
  - `schedules_insert_push_dedupe_series` — 시리즈 중복 알림 제거
  - `birthday_auto_calendar` — 생일 자동 등록 트리거 · sync 함수
  - `release_broadcasts_audit` — 릴리스 브로드캐스트 감사 로그
  - `schedules_push_policy_v2` — actor exclude · owner_kind=us 재정의
  - `users_everytime_identifier` — 에브리타임 identifier 컬럼 · CHECK 제약
  - `users_us_calendar_color` — 공동 색상 컬럼
  - `couples_dday_anchor_date` — 디데이 앵커 컬럼
  - `schedules_notify_insert_skip_dday_milestone` — milestone INSERT 알림 스킵
  - `schedule_source_add_dday_milestone` — enum 값 추가
  - `users_birthday_trigger_security_definer` — #326 근본 원인 (트리거 SECURITY DEFINER 승격)
  - `schedules_notify_skip_system_managed` — 시스템 자동 로우 (`birthday_uid` · `dday_milestone`) 는 INSERT/DELETE/UPDATE 알림 모두 스킵
- `send-announcement` Edge Function — 관리자 공지 FCM 발송.
- 에브리타임 XHR (`api.everytime.kr/find/timetable/table/friend`) 을 Ktor + xmlutil 로 파싱하는 `EverytimeRepository` 신설.

### iOS-specific
- 디데이 WidgetKit target 추가 — StaticConfiguration TimelineProvider + App Group payload → SwiftUI 렌더.
- 잠금화면 accessoryCircular 위젯 (mono tint 자동 처리).

### 인프라 · 빌드
- adminWeb 모듈 (`:adminWeb`) 신설 — Compose Multiplatform wasmJs, GitHub Pages 자동 배포 워크플로.

## [1.3.0] - 2026-09-15

### 신규 기능
- **Outlook 캘린더 연동** — 드로워에서 Microsoft 계정으로 로그인하면 내 Outlook 일정이 앱에 함께 표시돼요
- **캘린더 컬러 커스텀** — 8개 프리셋 외에도 색상휠 · 밝기 슬라이더 · HEX 코드 입력으로 원하는 색을 지정할 수 있어요
- **알림 시각 설정** — 일정/할 일 만들 때 언제 알림 받을지 직접 골라요
  - 시간 있는 일정: 정각 / 5분 / 10분 / 15분 / 30분 / 1시간 전 (기본 5분 전)
  - 종일 · 할 일: 하루 중 원하는 시각 (기본 오전 9시)
- **릴리즈 노트** — 드로워 하단에서 앱 버전별 변경사항을 바로 확인할 수 있어요

### 개선
- 편집 시트 (닉네임 · 커스텀 색상 · 반복 · 알림 등) 상단바 · "저장" 버튼 톤을 일정 추가 화면과 통일
- 닉네임 편집을 열면 커서가 텍스트 끝에 위치해 이어서 바로 수정 가능
- 편집 시트 배경을 은은한 회색으로 변경 (일정 추가 화면과 자연스럽게 연결)

### 버그 수정
- 프로필 캘린더 컬러를 바꿔도 홈/잠금화면 위젯에 반영되지 않던 문제 해결
- 색상휠 조작 시 "저장" 버튼 배경이 잠깐 사라지던 렌더링 문제 해결

## [1.3.0 · 개발자 노트]

> 이 섹션은 GitHub Release 본문에는 함께 게시되지만, 앱의 릴리즈 노트 화면에서는 렌더링 되지 않는다.
> `release.yml` 파서가 semver 헤더 사이만 추출해 이 섹션도 body 에 포함시키고, 앱의
> `ReleaseNotesScreen` 마크다운 렌더러가 `## [` 라인을 만나면 렌더링을 중단한다.

### 리팩터
- `SheetToolbar` / `SaveActionPill` / `CircleCloseButton` 공용화 (편집 시트 · CreateScheduleScreen 상단바)
- `AppInputCard` 를 String → 내부 `TextFieldValue` 관리로 개편 · `initialCursorAtEnd` 옵션
- `AppBottomSheet` `containerColor` 파라미터 추가
- `Color.toRgbHex` 공용 확장 (theme) 으로 승격 · TodayWidgetPayload 중복 헬퍼 제거
- Compose Skia iOS graphicsLayer 이슈 회피 — 저장 pill disabled 를 `.alpha()` modifier 대신 컬러 자체 opacity 로

### 인프라 · 빌드
- release-drafter 도입 — dev 머지 PR 라벨 기반 draft release 자동 초안
- gradle parallel · GC · CDS 튜닝
- `compile_kotlin_framework.sh` pre-warm (sim · device 두 아키텍처 동시 링크) — CONFIGURATION=Release 시엔 embedAndSign 하나만 실행하도록 분기 (Xcode Cloud archive OOM 회피)
- pbxproj `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED` 조기 종료 실제 제거 (Android Studio 에서 iosApp 실행 시 shared 재컴파일 스킵되던 문제)

### DB · 백엔드
- Migrations 2건 (`schedules.reminder_minutes_before`, `schedules.reminder_time`)
- `send_schedule_start_reminders()` REPLACE — offset 매칭 · per-row `reminder_time` 지원
- 위젯 payload 에 `meColorHex` · `partnerColorHex` · `usColorHex` 필드 추가 (앱 프로필 컬러 → 위젯)
- `ProfileEditViewModel.save.onSuccess` 에서 `refreshTodayWidget()` 호출

### iOS-specific
- MSAL init 실패 해소 — Info.plist `LSApplicationQueriesSchemes` 에 `msauthv2` · `msauthv3` 재등록
- Outlook 로그인 재탭 in-flight guard (MSAL 웹뷰 중첩 방지)

## [1.2.0] - 2026-09-08

### 신규 기능
- 파트너 프로필 카드 (커플 연결 화면)
- 자정 위젯 자동 갱신 (Silent Push 파이프라인)

### 개선
- 앱 아이콘 · 인앱 로고 단일 소스화 (사진 로고 교체)
- 드로워 "상대방 연결" 좌측 로고 정리

### 버그 수정
- 할 일 체크박스는 만든 사람만 토글 가능
- 위젯 sync delay 대응 · APNs 등록 entitlements 추가
- iOS FCM 토큰 upsert gap 커버

### 기타
- Xcode Cloud 아카이브 셋업 안정화
- kotlinx-datetime · Compose 클립보드 deprecation 정리

## [1.1.0] - 2026-09-04

### 신규 기능
- Apple 로그인 (iOS)
- Google 로그인 (iOS + Android)
- Firebase Crashlytics 자동 dSYM 업로드

### 개선
- 소셜 로그인 버튼 공통 컴포넌트 통합
- 프로필 셋업 온보딩에서 뒤로가기 = 로그아웃

### 기타
- Xcode Cloud 아카이브 셋업 (`ci_post_clone.sh`)
- release 브랜치 push 시 태그 · GitHub Release 자동 생성
