# 알림 내역 (#303)

사용자가 받은 푸시 알림을 최근 30일치 모아 보여주는 화면.

> 시안: ChatSea-App-Design · Section `4280:86006`
> - Screen 1 · List Filled / Screen 2 · Empty / Screen 3 · Loading / Screen 4 · Error
> - Screen 5 · Main Entry (진입점) / `Notification Row` 컴포넌트 (Type 4종)

---

## 1. 범위

| 포함 | 제외 |
|---|---|
| 메인 상단바 종 아이콘 → 알림 내역 화면 | 읽음 / 안 읽음 처리 · 뱃지 |
| 최근 30일 알림을 날짜별로 묶어 표시 | 알림 탭 시 해당 일정으로 이동 |
| 로딩 · 빈 상태 · 에러 (다시 시도) · 당겨서 새로고침 (#365) | 알림 삭제 · 종류 필터 · 페이지네이션 |
| 푸시 발송 시 서버에 내역 기록 | 알림 설정 (종류별 on/off) |

## 2. 진입점

- 메인 캘린더 상단바 **검색 아이콘 왼쪽**에 종 아이콘 (44dp 유리 원형 버튼, 버튼 사이 8dp).
- 상단바는 좌 1개 · 우 2개라 `Row(SpaceBetween)` 대신 `Box` 정렬로 바꿔 `2026. 9 ▾` 제목이 화면 정중앙에 유지되게 함.
- 읽음 처리가 없으므로 종 아이콘에 뱃지 없음.

## 3. 알림 종류

서버가 실제로 보내는 푸시 4종을 그대로 기록한다.

| kind | 발송 함수 | 계기 | 제목 · 본문 예 | 아이콘 |
|---|---|---|---|---|
| `partner` | `send-schedule-push` (INSERT/UPDATE/DELETE) | 상대방이 일정 · 할 일 추가 · 수정 · 삭제 | `민교 님이 할 일을 추가했어요` / `장보기 · 9월 23일` | 체크 박스 · 핑크 |
| `reminder` | `send-schedule-push` (START_REMINDER) | 일정 · 할 일 시작 알림 (pg_cron) | `치과 정기검진` / `오후 3:00 시작` | 종 · 파랑 |
| `announcement` | `send-announcement` | 관리자 공지 (adminWeb) | `서버 점검 안내` / 공지 본문 | 확성기 · 회색 |
| `update` | `broadcast-release-note` | 새 버전 배포 | `v1.5.0 업데이트` / `새 버전이 출시되었어요. …` | 반짝이 · 보라 |

- 수신자 규칙은 기존 푸시 정책 (#301) 과 동일 — 푸시를 받는 사람에게만 내역이 쌓인다.
- `partner` 본문 날짜는 내역용으로 `9월 27일` 포맷 (푸시 본문은 기존 그대로 `2026-09-27`).
- `update` 는 푸시 제목이 `🚨긴급🚨` 이라 내역에서는 버전을 제목으로 쓴다.

## 4. 화면

### 4.1 목록
- 상단바 `알림` (뒤로가기).
- 날짜 그룹: 라벨 (`오늘` · `어제` · `9월 20일 (일)`, 13sp SemiBold TextSecondary) + 흰 카드 (radius 12, 행 사이 divider 없음). 그룹 간격 20dp.
- 행: 36dp 원 아이콘 · 제목 15sp Medium (최대 2줄) + 우측 시간 12sp · 본문 13sp TextSecondary (최대 2줄).
- 시간 라벨: 오늘은 `방금` · `N분 전` · `N시간 전`, 그 이전은 그룹 라벨에 날짜가 있으므로 `오후 8:40` 처럼 시각만.
- 맨 아래 `최근 30일 동안 받은 알림만 보여요` (12sp, 가운데).
- 행 탭 동작 없음.

### 4.1.1 당겨서 새로고침 (#365)
- 목록 · 빈 상태에서 아래로 당기면 최근 30일치를 다시 조회 (`PullToRefreshBox`).
- 인디케이터: 흰 원 (`SurfaceCard`) + 파란 스피너 (`PrimaryBlue`), 상단 가운데.
- 새로고침 중에도 기존 리스트 유지 (스켈레톤으로 바꾸지 않음). 실패하면 기존 내역 그대로 두고 인디케이터만 닫음.
- 인디케이터는 최소 500ms 노출 — 조회와 대기를 동시에 돌려 `max(조회 시간, 500ms)` 후 닫힘.
- 캐시 없음: 매번 서버 조회. PostgREST 응답에 `Cache-Control` · `ETag` · `Last-Modified` 가 없고 Cloudflare 도 `DYNAMIC` 이라 iOS URL 캐시 · CDN 재사용 대상이 아님.
- 시간 라벨 (`N분 전`) 도 새로고침 시점 기준으로 다시 계산.
- 로딩 · 에러 상태에서는 당김 비활성 (에러는 `다시 시도` 버튼).

### 4.2 상태
| 상태 | 표시 |
|---|---|
| 로딩 | 그룹 라벨 자리 + 카드 안 스켈레톤 5줄 (`SkeletonLabel` · `SkeletonFill`) |
| 빈 상태 | 80dp 종 아이콘 · `아직 받은 알림이 없어요` · `일정을 등록하거나 / 일정이 곧 시작되면 여기에 모여요` |
| 에러 | `알림을 불러오지 못했어요` · `네트워크 연결을 확인하고 다시 시도해 주세요` · `다시 시도` 버튼 |

## 5. 데이터

### 5.1 테이블 `public.notifications`
(`supabase/migrations/20260923000000__notifications_history.sql`)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | uuid PK | |
| `user_id` | uuid → `users.id` (cascade) | 수신자 |
| `kind` | text check | `partner` · `reminder` · `announcement` · `update` |
| `title` · `body` | text | 표시 문구 |
| `schedule_id` | uuid null | partner · reminder 의 원본 스케줄 (FK 없음 — 스케줄 삭제돼도 내역 유지) |
| `dedupe_key` | text null | 재시도 중복 방지. `(user_id, dedupe_key)` unique |
| `created_at` | timestamptz | 받은 시각 |

- **RLS**: authenticated 는 `select` 만, 본인 row (`user_id = auth.uid()`) 만. 쓰기는 edge function 의 service role 만.
- **보관**: pg_cron `purge-old-notifications-daily` 가 매일 KST 04:00 에 30일 지난 row 삭제. 앱도 30일 이내만 조회.
- **dedupe**: `reminder:{id}:{date}:{time}` (pg_cron 중복 fire 대비) · `release:{version}` (브로드캐스트 재시도 대비). partial unique index 는 PostgREST upsert 가 42P10 을 내므로 일반 unique 제약 사용 — NULL 끼리는 충돌 안 함.

### 5.2 기록 시점
- 각 함수가 **device 조회 전에** 수신자 user 단위로 기록 → 푸시 토큰이 없는 사용자도 내역은 쌓인다.
- 기록 실패는 로그만 남기고 삼킨다 (`recordNotifications`) — 내역 때문에 푸시 발송이 막히지 않게.

### 5.3 앱 조회
- `NotificationsRepository.listSince(now - 30일)` — `user_id = 나` · `created_at >= since` · 최신순 · 최대 300건.
- 화면 진입마다 새로 조회 + 당겨서 새로고침 (캐시 · realtime 없음).

## 6. 파일

**서버**
- `supabase/migrations/20260923000000__notifications_history.sql` — 테이블 · RLS · 정리 cron
- `supabase/functions/send-schedule-push` · `send-announcement` · `broadcast-release-note` — `recordNotifications` 추가

**앱**
- `data/remote/NotificationsRepository.kt`
- `feature/notification/NotificationsRoute.kt` · `NotificationsScreen.kt` · `NotificationsViewModel.kt`
- `feature/main/MainScreen.kt` · `MainRoute.kt` · `App.kt` — 종 아이콘 · 네비게이션
- `designsystem/theme/Color.kt` — `Noti*` 종류별 색 · `Skeleton*`
- `composeResources/drawable/` — `ic_bell` · `ic_bell_empty` · `ic_noti_schedule` · `ic_megaphone` · `ic_sparkles`
- `commonTest/.../feature/notification/NotificationsTest.kt` — 날짜 묶기 · 시간 라벨

## 7. 배포 순서

1. 마이그레이션 적용 (테이블 생성) — 수동 (MCP / CLI). 추가만 하는 변경이라 기존 앱에 영향 없음.
2. PR 머지 → `supabase-deploy` 워크플로가 바뀐 함수 3개 자동 배포.
   - 함수가 먼저 배포돼도 테이블이 없으면 기록만 실패하고 푸시는 정상 발송.
3. 앱 릴리스 — 배포 이후 받은 알림부터 내역에 보인다 (과거 알림은 기록이 없음).

## 8. 후속 후보

- 알림 탭 → 해당 일정 · 할 일로 이동 (`schedule_id` 이미 저장 중)
- 읽음 처리 · 종 아이콘 뱃지
- 스와이프 삭제 · 종류 필터
