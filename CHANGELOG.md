# Changelog

이 프로젝트의 모든 릴리즈 노트를 여기에 기록한다. 포맷은
[Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 를 따르고,
버전은 [Semantic Versioning](https://semver.org/lang/ko/) 을 따른다.

버전 소스는 `iosApp/Configuration/Config.xcconfig` 의 `MARKETING_VERSION`
이며, 이 파일의 최상단 (Unreleased 제외한 첫 `## [X.Y.Z]` 섹션) 이 GitHub
Release 노트로 자동 게시된다 (`.github/workflows/release.yml`).

## [Unreleased]

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
