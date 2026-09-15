# Changelog

이 프로젝트의 모든 릴리즈 노트를 여기에 기록한다. 포맷은
[Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 를 따르고,
버전은 [Semantic Versioning](https://semver.org/lang/ko/) 을 따른다.

버전 소스는 `iosApp/Configuration/Config.xcconfig` 의 `MARKETING_VERSION`
이며, 이 파일의 최상단 (Unreleased 제외한 첫 `## [X.Y.Z]` 섹션) 이 GitHub
Release 노트로 자동 게시된다 (`.github/workflows/release.yml`).

## [Unreleased]

## [1.3.0] - 2026-09-15

### 신규 기능
- Outlook 캘린더 동기화 (MSAL 로그인 · Graph API mirror · 드로워 진입)
- 캘린더 컬러 커스텀 (HSV 색상휠 + Brightness 슬라이더 + hex 코드 입력)
- 스케줄 알림 시각 사용자 설정
  - 시간 있는 일정: 정각 / 5·10·15·30분 전 / 1시간 전 (기본 5분 전)
  - 종일 · 할 일: 하루 중 알림 시각 (5분 스텝, 기본 09:00)
- 드로워에 릴리즈 노트 항목 추가 (GitHub Releases 실시간 조회 · 접기/펼치기)

### 개선
- 편집 시트 상단 툴바 통일 (`SheetToolbar`) — 일정 추가 화면과 톤 일치
- `SaveActionPill` 공용화 (일정 추가 · 프로필 편집 시트 공통)
- `AppInputCard` TextFieldValue 개편 · 커서 위치 제어 옵션 추가 (닉네임 시트 편집 UX)
- `AppBottomSheet` `containerColor` 파라미터 추가 · 편집 시트는 `SurfaceGray` 로 통일
- Compose Skia iOS graphicsLayer 이슈 회피 — 저장 pill disabled 를 color opacity 로 처리

### 버그 수정
- 위젯이 유저 캘린더 컬러 미반영 문제 (payload 컬러 hex 동기화 · 프로필 저장 시 위젯 refresh 트리거)
- Android Studio 에서 iosApp 실행 시 shared 재컴파일 스킵되던 문제 (pbxproj OVERRIDE 조기 종료 제거)
- MSAL SDK init 실패 (Info.plist `msauthv2` · `msauthv3` 쿼리 스킴 재등록)

### 기타
- release-drafter 도입 — dev 머지 PR 라벨 기반 draft release 자동 초안
- gradle parallel · GC · CDS 튜닝 · compile-kotlin-framework 프리워머
- Supabase migrations 2건 (`schedules.reminder_minutes_before`, `schedules.reminder_time`) · `send_schedule_start_reminders` REPLACE

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
