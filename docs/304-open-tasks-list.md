# 할 일 내역 페이지 (#304)

내가 맡았거나 공동으로 맡은 할 일 (`type='task'`) 을 **남은 일 / 끝낸 일** 로 나눠 스캔 · 체크 · 편집하는 리스트 화면.

> 시안: ChatSea-App-Design · 페이지 `양현진`
> - 드로워 진입점 `4280:85916`
> - 리스트 `4280:85790` (남은 일) · 정렬 메뉴 열림 `4280:85830` (끝낸 일)

---

## 1. 진입점

- 드로워 하단 텍스트 행 `할 일` → `릴리즈 노트` → `로그아웃` 순서. 새 행은 `TasksRow`.
- 세 행 모두 리플 피드백 (좌우 8dp 인셋 · 10dp 라운드). 텍스트 시작 위치는 기존과 같은 20dp.
- 카운트 뱃지는 없음 (드로워 열 때마다 쿼리가 생기므로).

## 2. 화면 구성

```
[←]            할 일
┌ 남은 일 │ 끝낸 일 ┐          ← SegmentedControl
                 최신순 ≡↓     ← 정렬 버튼
┌──────────────────────────┐
│ [ ] 분리수거 배출      오늘 │  ← 흰 카드 한 장 (radius 12), 행 사이 divider 없음
│ [ ] 병원 예약 확정 전화 어제 │
│ [ ] 치과 정기검진  25.12.11 │
└──────────────────────────┘
```

- 행: 체크박스 22dp (radius 6) · 제목 15sp Medium (최대 2줄) · 날짜 13sp SemiBold TextSecondary. 높이 62 (상하 20).
  - 미완료 체크박스: 1.5dp `TaskCheckBorder` (#AFAFB4) 테두리
  - 완료 체크박스: `DrawerCheckBlue` fill + 흰 체크
- 날짜 라벨: `오늘` · `어제` · `내일` / 올해는 `M.d` / 다른 해는 `yy.M.d`.
- 빈 상태: "남은 할 일이 없어요" · "끝낸 할 일이 없어요". 로딩 · 에러 (다시 시도) 는 릴리즈 노트 화면과 같은 패턴.

## 3. 데이터

| 탭 | 쿼리 | 조건 |
|---|---|---|
| 남은 일 | `SchedulesRepository.listOpenTasks(today)` (위젯과 공용) | `is_done=false AND start_date <= today` |
| 끝낸 일 | `SchedulesRepository.listDoneTasks()` (신규) | `is_done=true` · 날짜 상한 없음 |

- **남은 일에 날짜 상한을 두는 이유**: 반복 할 일은 미래 인스턴스가 미리 materialize 돼 있어, 상한이 없으면 리스트가 수백 개로 불어난다.
- **owner 필터**: `resolveOwnerForViewer` 로 뷰어 관점 변환 후 `partner` 제외 → 나 · 공동만.
- 두 탭 데이터를 진입 시 한 번에 받아 두고, 탭 전환 · 정렬은 로컬 처리 (재조회 없음).
- DB · 마이그레이션 변경 없음 (RLS `schedules_all_in_my_couple` 재사용).

## 4. 정렬

- 메뉴 항목: **최신순** (기본) / **과거순**. 기준은 할 일 날짜 (`start_date`), 같은 날짜는 제목순.
- 메뉴: 폭 160 · 항목 44 · radius 12, 선택 항목은 좌측 체크 + SemiBold. 항목 사이 divider 없음.
- 그림자: Figma 값 그대로 `dropShadow` 두 겹 — (0, 8) blur 24 · 12% (`MenuShadow`) + blur 1 · 4% (`MenuOutlineShadow`).
  elevation 기반 `shadow()` 는 플랫폼 기본 알파가 곱해져 거의 안 보임.
- 애니메이션: 메뉴 본체 우상단 기준 scale (0.6 → 1) + fade. 메뉴 둘레에 그림자 여백을 둬 애니메이션 중에도 그림자가 잘리지 않게 함.
- 바깥 탭 1회 (touch down) 에 즉시 닫힘. `Popup` 대신 화면 안 오버레이로 그려 직접 처리. 바깥 탭은 뒤 콘텐츠로 전달하지 않음.
- 정렬 변경 시:
  - 스크롤은 key 가 아닌 **현재 인덱스 · 오프셋 유지** (`LazyListState.requestScrollToItem`).
  - 행은 미끄러지지 않고 **fade out → fade in** — LazyColumn key 에 정렬 기준을 섞어 전체를 교체.

## 5. 인터랙션

- **체크박스 탭**: 옵티미스틱으로 반대 탭으로 이동 → `setTaskDone` → 성공 시 `refreshTodayWidget()` + 캘린더 재fetch 트리거 (`scheduleRefreshTick++`). 실패 시 원복.
- **행 탭**: `CreateScheduleRoute(id)` 편집 진입. 저장 후 복귀하면 `scheduleRefreshTick` 변화를 받아 스피너 없이 재조회.
- 삭제는 편집 화면에서만 (기존 흐름).

## 6. 파일

**추가**
- `feature/task/TasksRoute.kt` — 라우트 · tick 기반 재조회
- `feature/task/TasksScreen.kt` — 화면 · 정렬 메뉴 오버레이
- `feature/task/TasksViewModel.kt` — 상태 · 로드 · 토글 (`TaskTab` · `TaskSort` · `TaskItem`)
- `composeResources/drawable/ic_sort.xml` — Figma `ci:sort-ascending`
- `commonTest/.../feature/task/TasksTest.kt` — 날짜 라벨 · 탭 / 정렬 로직

**수정**
- `SchedulesRepository.kt` — `listDoneTasks()`
- `MainDrawer.kt` · `MainScreen.kt` · `MainRoute.kt` — `onTasksClick` 전파, 하단 행 리플
- `App.kt` — `TasksRoute` NavKey · entry
- `Color.kt` — `TaskCheckBorder` · `MenuShadow` · `MenuOutlineShadow`

## 7. 후속 후보

- 스낵바 "실행 취소" (체크 직후 되돌리기)
- 정렬 선택 기억 (지금은 진입마다 최신순)
- 끝낸 일 페이지네이션 (완료 이력이 많아질 때)
