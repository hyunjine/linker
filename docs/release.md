# 릴리즈 가이드

Linker 의 브랜치 · 버전 · 릴리즈 흐름 (#433). `scripts/create-dev-branch.sh` · `iosApp/ci_scripts/ci_post_clone.sh` ·
`.github/workflows/release.yml` · `release-check.yml` · `Config.xcconfig` 의 버전 값을 고치기 전에 읽는다.

## 한눈에 보기

| 단계 | 트리거 | 하는 일 | 사람이 할 일 |
|---|---|---|---|
| 버전 시작 | Admin 이 `scripts/create-dev-branch.sh X.Y.Z` | `release` 에서 `develop/vX.Y.Z` 생성 · `MARKETING_VERSION=X.Y.Z` 커밋 · default branch 변경 | 새 base 브랜치 알리기 |
| 개발 | 작업 브랜치 → `develop/vX.Y.Z` PR | 함수 배포 · DB 마이그레이션 (`production` 승인) · 관리자 웹 배포 | 리뷰 · 머지 · 승인 |
| 승격 PR | `develop/vX.Y.Z → release` PR 열기 | Release Check 가 버전 · 브랜치 이름 · CHANGELOG 일치 확인 | `CHANGELOG.md` 에 `## [X.Y.Z] - 날짜` 섹션이 들어가 있는지 확인 |
| 릴리즈 | 승격 PR 머지 | Release Automation 이 태그 · GitHub Release `vX.Y.Z` 생성, Xcode Cloud 가 archive → TestFlight | TestFlight 처리 완료 · 배포 알림 확인 |

## 브랜치

- 장기 브랜치: `release` (TestFlight 에 나간 코드) ← `develop/vX.Y.Z` (그 버전의 개발 통합).
  TestFlight 내부 배포만 하므로 QA 용 중간 브랜치는 두지 않는다.
- 새 `develop/vX.Y.Z` 는 `release` 에서 갈라진다. 이전 개발 브랜치에 릴리즈 뒤 머지된 커밋은 새 브랜치에
  안 들어간다 — 스크립트가 개수 · 목록을 보여 주고 진행할지 묻는다. 가져가려면 `--source <이전 개발 브랜치>`.
- 레포 default branch 는 항상 지금 개발 중인 `develop/vX.Y.Z` 다. PR 의 기본 base 가 된다.
- 이전 개발 브랜치는 승격 · 마무리가 끝나면 지운다.

## 버전

| 값 | 어디에 | 누가 정하나 |
|---|---|---|
| 버전 이름 `X.Y.Z` | `iosApp/Configuration/Config.xcconfig` 의 `MARKETING_VERSION` (iOS · Android versionName · 앱 내 표시 공통) | `develop/vX.Y.Z` 를 만들 때 `scripts/create-dev-branch.sh`. 손으로 바꾸면 승격 PR 의 Release Check 가 브랜치 이름과 비교해 막는다 |
| iOS 빌드 번호 | Xcode Cloud `CI_BUILD_NUMBER` | Xcode Cloud. `iosApp/ci_scripts/ci_post_clone.sh` 가 archive 전에 `CURRENT_PROJECT_VERSION` 을 덮어쓴다 |
| 로컬 빌드 번호 | `Config.xcconfig` 의 `CURRENT_PROJECT_VERSION=1` | 고정. 올리지 않는다 |
| Android versionCode | `MARKETING_VERSION` 에서 계산 (`androidApp/build.gradle.kts`) | 자동 |

- 릴리즈마다 올리던 버전 bump PR 은 없다. CI 는 브랜치에 커밋하지 않는다.
- 같은 버전을 다시 내보낼 때 (archive 실패 재시도 · 수정 후 재승격) 도 번호를 손댈 필요가 없다 —
  Xcode Cloud 빌드마다 번호가 늘어난다. `vX.Y.Z` 태그가 이미 있으면 Release Automation 은 건너뛴다.
- Xcode Cloud 빌드 번호가 기존 TestFlight 빌드보다 작아지면 (워크플로를 새로 만든 경우 등) 업로드가 거절된다.
  App Store Connect → Xcode Cloud → 설정 → Build Number 에서 다음 빌드 번호를 올린다.

## 릴리즈 노트

- `CHANGELOG.md` 의 `## [X.Y.Z] - 날짜` (사용자 노트) + `## [X.Y.Z · 개발자 노트]` 섹션을 개발 브랜치에서
  일반 PR 로 써 둔다. 승격 PR 머지 시 Release Automation 이 최상단 버전 섹션을 GitHub Release 본문으로 올린다.
- 앱의 릴리즈 노트 화면 · "최신 버전" 표시는 GitHub Release 를 읽는다.

## CI 가 보는 브랜치

| 워크플로 | 트리거 |
|---|---|
| `supabase-deploy.yml` · `supabase-migrate.yml` · `adminweb-deploy.yml` · `release-drafter.yml` | `develop/**` push |
| `release-check.yml` | `release` 대상 PR |
| `release.yml` | `release` push |
| Xcode Cloud archive | `release` 변경 (App Store Connect 에서 설정) |

- GitHub Environment `production` 은 `develop/*` 브랜치의 배포만 허용한다 (승인자: 오너).
