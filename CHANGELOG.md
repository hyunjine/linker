# Changelog

이 프로젝트의 모든 릴리즈 노트를 여기에 기록한다. 포맷은
[Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 를 따르고,
버전은 [Semantic Versioning](https://semver.org/lang/ko/) 을 따른다.

버전 소스는 `iosApp/Configuration/Config.xcconfig` 의 `MARKETING_VERSION`
이며, 이 파일의 최상단 (Unreleased 제외한 첫 `## [X.Y.Z]` 섹션) 이 GitHub
Release 노트로 자동 게시된다 (`.github/workflows/release.yml`).

## [Unreleased]

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
