# Changelog

이 프로젝트의 모든 릴리즈 노트를 여기에 기록한다. 포맷은
[Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 를 따르고,
버전은 [Semantic Versioning](https://semver.org/lang/ko/) 을 따른다.

버전 소스는 `iosApp/Configuration/Config.xcconfig` 의 `MARKETING_VERSION`
이며, 이 파일의 최상단 (Unreleased 제외한 첫 `## [X.Y.Z]` 섹션) 이 GitHub
Release 노트로 자동 게시된다 (`.github/workflows/release.yml`).

## [Unreleased]

## [1.7.0] - 2026-11-18

### 신규 기능
- **사진첩** — 커플 사진을 날짜에 붙여 모아 볼 수 있어요. 드로워의 "사진첩"에서 달별로 모아 보고, 캘린더 날짜를 누르면 그날 사진을 바로 보거나 추가할 수 있어요. 사진은 우리 커플만 볼 수 있고, 올린 사람만 지울 수 있어요.
- **Outlook 재택 일정 연동** — 더보기 → 캘린더 연동에서 회사 Outlook 계정을 연결하고 팀 캘린더 이름을 입력하면, 팀 캘린더에 있는 내 재택 일정이 캘린더에 자동으로 들어와요. 앱에서 시간을 바꾸거나 지우면 Outlook 에도 반영돼요.
- 앱을 열지 않아도 30분마다 재택 일정을 새로 가져와요 (새벽 1시 ~ 6시 제외).

### 개선
- 크리스마스 시즌 새 앱 아이콘 🎄 — 전구와 산타 모자, 선물 상자로 꾸몄어요. 시작 화면 로고도 함께 바뀌었어요.
- 앱을 켤 때 시작 화면에서 더 빨리 넘어가요.
- 네트워크가 끊긴 채 앱을 켜면 시작 화면에 연결을 확인해 달라는 안내가 보여요. 연결되면 자동으로 이어서 진행돼요.

### 버그 수정
- 위젯을 누르거나 앱으로 돌아왔을 때 캘린더 일정이 비어 보이던 문제 해결.
- 앱으로 돌아올 때 가끔 앱이 종료되던 문제 해결.
- 이미 가입했는데 앱을 켤 때 프로필 설정 화면이 잠깐 보였다가 메인으로 넘어가던 문제 해결.
- 앱으로 돌아왔을 때 로그인 화면이 잠깐 비치던 문제 해결.
- 로그인이 만료됐는데도 메인 화면에 머물러 일정이 보이지 않던 문제 해결. 이제 로그인 화면으로 안내해요.
- 아이폰 카메라로 찍은 세로 사진이 프로필 사진에서 눕혀져 들어가던 문제 해결.

## [1.7.0 · 개발자 노트]

> 이 섹션은 GitHub Release 본문에는 함께 게시되지만, 앱의 릴리즈 노트 화면에서는 렌더링 되지 않는다.

### 신규 기능
- 사진첩 (#338 관련, #429 · #432) — `couple_photos` 테이블 + RLS (같은 커플 select · 본인 insert · 올린 사람만 delete, update 없음), private `couple-photos` 버킷 (5MB · image/jpeg, 경로 `{couple_id}/{photo_id}.jpg` · `_thumb.jpg`). 업로드는 파일 먼저 · row 나중 (row 실패 시 파일 되지움). JPEG 85 · 원본 긴 변 2048px · 썸네일 400px. Coil 캐시 키를 `storage_path` 로 고정 (signed URL 토큰이 매번 달라 캐시 미스). `couple_photos` realtime 은 별도 채널 (마이그레이션 전에도 기존 구독 안전). 사진 id 는 클라이언트 생성 UUID (`RandomUuid.kt` 공용화). 저장소는 #338 의 Cloudflare R2 가 아닌 Supabase Storage — 백엔드 결정은 후속.
- Outlook TeamAIVN 재택 동기화 (#242) — MSAL 을 회사 테넌트 단일 등록으로 교체 (authority 테넌트 ID 고정). 공유 캘린더 `TeamAIVN` 의 `재택(이름) HH:MM-HH:MM` 중 팀 캘린더 이름 (기기 로컬 `OutlookLocal`) 이 같은 것만 미러링 (`RemoteWorkSubject`). 앱에서 수정 · 삭제 시 원본 제목 시각만 교체 · 원본 삭제 (null 필드 미전송 PATCH, 앱 전역 scope 에서 반영). 더보기 → 캘린더 연동 UI. 동기화 시점: 세션 복원 · 커플 변경 · 연결 · 이름 저장 · ON_RESUME (1분 dedupe). 개인 Microsoft 계정 미지원, Android 는 디버그 키 서명 해시만 등록.
- Outlook 30분 주기 백그라운드 동기화 (#420) — Graph 웹훅 불가 (남의 공유 캘린더) 라 silent push polling. pg_cron `*/30 0-15,21-23 * * *` (UTC, KST 01:00 ~ 05:59 제외) → Edge Function `send-outlook-sync-push` (`x-outlook-sync-secret`, Vault `outlook_sync_push_secret`) → `users.outlook_connected_at` 이 있는 유저 기기에 `data { type: "outlook_sync" }`. iOS `LinkerPushBridge` · Android `LinkerFirebaseMessagingService` 가 `OutlookBackgroundSync` 실행 (20초 timeout, 프로세스 전역 Mutex 로 foreground 와 직렬화). 예전 Android 앱은 data 메시지에 빈 알림을 띄울 수 있음.

### 개선
- 크리스마스 시즌 앱 아이콘 (#435) — Figma `5754:116322`. iOS `app-icon-1024.png` (RGB), Android `ic_launcher` · `ic_launcher_round` · `ic_launcher_foreground` (흰 배경 70%) 전 해상도, 앱 내 `ic_app_logo.png` 도 교체. 스플래시 · 로그인 타이틀 `"현진이랑 민교"` → `"현진이랑민교"`.
- FCM 기기 등록 비차단 (#425) — `ensureCurrentDeviceRegistered()` 를 App 레벨 scope 로 분리해 스플래시 라우팅이 기다리지 않음.
- 스플래시 오프라인 안내 (#424) — 스플래시가 5초 이상 유지되면 하단 비차단 안내 fade-in (`AuthGateScreen(networkHint)`). `NotAuthenticated` 확정 시 미노출.
- `ImageEncode` 를 크기 지정 방식으로 일반화 (`encodeAvatarJpeg()` 는 래퍼 유지), iOS `ImagePicker` EXIF 방향 보정 — 아바타에도 적용 (#432).

### 버그 수정
- 재진입 시 일정 누락 · 앱 종료 (#422) — 조회 실패 시 캐시 미변경, in-flight `Map<YearMonth, Job>` (재진입 · 복구 시 stale cancel), resume 재조회 (`LifecycleResumeEffect` → `MainViewModel.onResume()`), 세션 게이트 `awaitUsableSession` (`data/remote/SessionReadiness.kt`, 위젯과 공유), `RefreshFailure → Authenticated` 복귀 시 재조회, 재시도 1 · 2 · 4 · 8초 backoff 최대 5회, `subscribeCoupleRealtime` 예외 처리 (Kotlin/Native 크래시).
- 스플래시 라우팅 (#424) — `BootstrapProfile.kt`: 프로필 조회 3회 재시도, 조회 실패 (`Unknown`) 와 미완성 확정 (`Incomplete`) 구분 → ProfileSetup 은 미완성 확정 시에만. 부트스트랩 라우팅 로그인 세션당 1회, `RefreshFailure` 는 백스택 유지 (AuthRoute 리셋은 `NotAuthenticated` 에서만).
- 포그라운드 복귀 세션 검사 (#437) — iOS 는 supabase-kt lifecycle 처리가 없어 만료 토큰이 `Authenticated` 로 남음. `checkSessionHealth` (`GET /user` · refresh, 408 · 429 제외 4xx 만 `Gone`) 를 부트스트랩 이후 30초 간격으로 실행, `Gone` 이면 `clearLocalSession` → 로그인. 부트스트랩 폴백도 세션 소멸이면 Main 대신 로그인.
- Outlook 미러 insert DTO 기본값이 직렬화 (encodeDefaults=false) 에서 빠져 `type` NOT NULL 위반 (#242).
- 사진첩 사진 id 를 UUID 로 — `uuid` 컬럼 insert 실패 (#432).

### DB · 백엔드
- Migration `20261001075137__unschedule_outlook_ics_sync` (초기 ICS cron 해제) · `20261006235930__schedules_notify_skip_outlook` (Outlook 미러 파트너 푸시 제외) (#242) — 운영 적용 완료.
- Migration `20261028075745__users_outlook_connected_at` · `20261028075838__pg_cron_outlook_sync_push` (#420) — 운영 적용 완료. Edge Function `send-outlook-sync-push` 배포 완료.
- Migration `20261103054434__couple_photos` (#432) — 테이블 · RLS · 버킷 · realtime publication 추가, `private.delete_empty_couple()` 트리거 교체 (빈 커플 정리 시 Storage 오브젝트도 삭제). `supabase-migrate.yml` 로 적용.

### 인프라 · 빌드
- DB 마이그레이션 CI 적용 (#430) — repo ↔ 프로덕션 ledger 정렬 (35개 rename · 11개 복원), `supabase-migrate.yml` (link → dry-run → `db push`, GitHub Environment `production` 승인 게이트). `supabase-deploy.yml` 도 `detect` / `deploy-functions` 로 분리해 승인 게이트 적용. MCP · SQL Editor 직접 적용 금지.
- 브랜치 모델을 `dev → release` 에서 `develop/vX.Y.Z → release` 로 변경 (#433). 버전마다 `scripts/create-dev-branch.sh X.Y.Z` 로 개발 브랜치를 열고, 그때 `MARKETING_VERSION` 을 한 번 커밋한다 — 릴리즈마다 올리던 버전 bump PR 폐지. 승격 PR 은 `release-check.yml` 이 버전 · 브랜치 이름 · CHANGELOG 일치를 확인한다. 흐름은 `docs/release.md`.
- iOS 빌드 번호를 Xcode Cloud `CI_BUILD_NUMBER` 로 (#433). `iosApp/ci_scripts/ci_post_clone.sh` 가 archive 전에 `CURRENT_PROJECT_VERSION` 을 덮어쓴다. 저장소 값은 로컬 빌드용 고정값 `1`.
- `dev` push 에 걸려 있던 워크플로 (`supabase-deploy` · `supabase-migrate` · `adminweb-deploy` · `release-drafter`) 를 `develop/**` 로 변경.

## [1.6.3] - 2026-10-01

### 개선
- 가을맞이 새 앱 아이콘 🍂 — 바닥에 낙엽이 소복이 쌓였어요.

## [1.6.3 · 개발자 노트]

> 이 섹션은 GitHub Release 본문에는 함께 게시되지만, 앱의 릴리즈 노트 화면에서는 렌더링 되지 않는다.

### 개선
- 가을 시즌 앱 아이콘 (#416) — Figma 4527:79052. iOS `app-icon-1024.png` (RGB), Android `ic_launcher` · `ic_launcher_round` · `ic_launcher_foreground` (흰 배경 70% 배치) 전 해상도. 앱 내 `ic_app_logo.png` 는 유지.

## [1.6.2] - 2026-10-01

### 버그 수정
- 홈 화면 달력 위젯에서 10월 · 11월 · 12월이 "1…" 로 잘려 보이던 문제 해결.

## [1.6.2 · 개발자 노트]

> 이 섹션은 GitHub Release 본문에는 함께 게시되지만, 앱의 릴리즈 노트 화면에서는 렌더링 되지 않는다.

### 버그 수정
- 캘린더 위젯 월 라벨 말줄임 (#412) — `CalendarWidget.swift` 월 라벨이 미니 달력 7칸 중 첫 칸 폭 (≈ 1/7) 으로 제한돼 두 자리 달이 잘림. 첫 칸은 숨김 텍스트로 높이만 잡고 실제 라벨은 `overlay` + `fixedSize()` 로 그려 "일" 헤더와 center 정렬 유지.

### 인프라 · 빌드
- 1.6.1 배포 알림이 App Store Connect 웹훅 (#395) 으로 자동 발송됨을 확인 (`release_broadcasts.source = asc-webhook`).

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
