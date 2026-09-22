# 만난 날 D+N 카운터 (#182)

커플이 처음 만난 날짜(=사귀기 시작한 날)를 저장해두고, "우리가 만난 지 며칠째" 를 카운터로 보여주는 단일 기능. "기념일 목록" 이 아니라 **관계 카운터 위젯 하나**로 스코프를 좁혔다.

> 범위: **드로워 진입 · 카운터 화면 · 만난 날 편집 시트** 세 화면. 마일스톤 (100일 · 200일 · 1000일 등) 자동 감지 · 알림 발송 · 홈 위젯 노출.
> 비범위: 여러 기념일 목록 관리 · 개별 기념일 편집 · 메모 · 검색은 후속.

---

## 1. 목적 · 배경

- 커플 앱에서 "우리 사귄 지 오늘로 며칠?" 을 즉시 볼 수 있는 것은 관계 몰입도의 기본 signal.
- 여러 기념일을 관리하는 앱들과 달리 Linker 는 **딱 하나의 기준일 (만난 날)** 만 저장하고, 그로부터 파생되는 카운트 · 마일스톤을 자동으로 계산해 보여주는 방식으로 집중.
- 100일 · 200일 · 1주년 같은 특별 카운트에 도달하면 자동 push 알림.

## 2. 사용자 흐름

```
[드로워]                      →  [만난 날 카운터]                    →  [만난 날 편집 시트]
  └─ "우리 만난 날" 탭            ├─ 큰 D+N일 카운터                     ├─ 날짜 피커
                                  ├─ 시작일 · 다음 마일스톤 카드            └─ [저장]
                                  ├─ 마일스톤 리스트 (지난 · 다음)
                                  └─ [편집] 버튼 → 편집 시트
```

세션 만료 · 커플 미가입 상태에서 진입 → 안내 후 드로워로 back. 커플 미가입이면 "파트너 연결 후 사용해요" empty state.

## 3. 화면 구성

### 3.1 드로워 진입점

`MainDrawer` 의 "우리 만난 날" 행:
- 좌측 22dp 하트 아이콘 (`ic_heart_outline.xml` 신설 — 링크 아이콘과 별개)
- 라벨: `우리 만난 날` + 우측 회색 서브텍스트 `D+N일` 로 즉시 카운트 노출 (탭 안 해도 보이도록)
- 탭 → `MeetingDayCounterRoute`

미가입 상태에는 서브텍스트 숨김, 라벨만 노출.

### 3.2 카운터 화면 (`MeetingDayCounterScreen`)

```
┌────────────────────────────────────────────────────────────┐
│  ←  우리 만난 날                                 [ ✏️ 편집 ] │  ← Top bar
├────────────────────────────────────────────────────────────┤
│                                                            │
│                                                            │
│                   💜  · Purple gradient BG ·                │
│                                                            │
│                                                            │
│                       D + 297일                             │  ← Big counter (72sp Bold)
│                                                            │
│                     2025. 03. 22 부터                        │  ← Anchor date (14sp)
│                                                            │
│                                                            │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  🎉 다음 마일스톤                                        │ │  ← Next milestone card
│  │  D-3 · 300일까지                                        │ │
│  │  2026. 01. 15                                          │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                            │
│  ── 지나온 마일스톤 ─────────────────────────                  │
│  ●  100일    2025.06.30                    ✓                │
│  ●  200일    2025.10.08                    ✓                │
│                                                            │
│  ── 앞으로의 마일스톤 ────────────────────────                 │
│  ○  300일    2026.01.15   · D-3 ·                           │
│  ○  1주년    2026.03.22   · D-70 ·                          │
│  ○  500일    2026.08.04   · D-204 ·                         │
│  ○  1000일   2027.12.17   · D-635 ·                         │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

| 위젯 | 설명 |
|---|---|
| `MeetingTopBar` | 좌: 뒤로가기 · 중앙: "우리 만난 날" · 우: 연필 편집 아이콘 |
| `HeroCounter` | 상단 40% 를 차지하는 Purple 그라디언트 영역. `D + N일` 큰 텍스트 + 앵커 날짜 서브 |
| `NextMilestoneCard` | 다가오는 마일스톤 1개 (D-N + 라벨 + 날짜) · 없으면 숨김 |
| `MilestoneSection · past` | 지나온 마일스톤 리스트 · 체크 표시 |
| `MilestoneSection · future` | 앞으로의 마일스톤 리스트 · D-N 표시 |
| `EmptyState (커플 미가입)` | 하트 일러스트 + "파트너 연결 후 사용해요" + [파트너 연결] 버튼 |
| `EmptyState (앵커 미설정)` | 큰 물음표 카운터 자리 + [만난 날 설정] 버튼 → 편집 시트 |

**마일스톤 규칙 (자동 생성)**:
- **100일 단위**: 100 · 200 · 300 · 400 · 500 · 600 · 700 · 800 · 900
- **1000일 단위**: 1000 · 2000 · 3000 · …
- **N주년**: 365 · 730 · 1095 · … (윤년 무시, 365 곱셈으로 근사. UI 는 "1주년" 표시하되 실제 발동은 정확히 365N 일)
- **동시 표시**: 300일 과 1주년이 동시에 뜨면 둘 다 리스트 표시. 다음 마일스톤 카드에는 가장 가까운 하나.

### 3.3 만난 날 편집 시트 (`MeetingDayEditSheet`)

```
┌───────────────────────────────────────────────────┐
│  ── (drag handle) ──                              │
│                                                   │
│  만난 날 편집                                       │
│                                                   │
│  ┌─────────────────────────────────────────────┐ │
│  │  2025    ▾    03    ▾    22    ▾           │ │  ← 년/월/일 각각 wheel picker
│  └─────────────────────────────────────────────┘ │
│                                                   │
│  현재 D + 297일                                    │  ← 실시간 미리보기 (앵커 바꿀 때마다)
│                                                   │
│  [               저 장                ]           │
└───────────────────────────────────────────────────┘
```

| 요소 | 설명 |
|---|---|
| `WheelPickers` | iOS 스타일 3 wheel · 년/월/일. 미래 날짜 금지 (오늘 이후는 disabled) · 최소 1900-01-01 |
| `LivePreview` | 사용자가 wheel 조작할 때마다 즉시 "D+N일" 갱신. 앵커 무결성 감각을 준다 |
| `SaveButton` | 저장 시 `couples.meeting_date` upsert + 마일스톤 재계산 · realtime 으로 파트너 즉시 반영 |

**첫 설정 시**: 상단 문구 "만난 날 편집" → "만난 날 설정" 으로 바뀌고, `LivePreview` 는 "설정 후 표시" placeholder.

**삭제**: 별도 UI 없음 (앵커는 커플 필수 데이터로 취급). 만난 날을 잘못 설정한 경우 수정만 가능.

## 4. 캘린더 chip 표시

메인 캘린더의 **만난 날 (앵커일)** 셀과 **마일스톤 날짜** 셀에 chip 자동 표시:

- **앵커일 chip**: 💜 만난 날 · Purple 팔레트 (`ChipMilestoneBg` = `#F3EBFF` · `ChipMilestoneText` = `#7B4EE6`)
- **마일스톤 chip**: `100일` · `1주년` 같이 라벨만. 클릭 → 카운터 화면으로 이동

앵커일 이전 셀에는 아무것도 표시하지 않는다.

## 5. 데이터 모델

### 5.1 신규 컬럼

`public.couples`:
```sql
alter table public.couples
    add column if not exists meeting_date          date,
    add column if not exists meeting_reminder_hour smallint not null default 9;
```

- `meeting_date` NULL 이면 아직 설정 안 된 상태
- `meeting_reminder_hour` — 마일스톤 발동 시각 (KST 0-23 시)

### 5.2 마일스톤 테이블 X

마일스톤은 **저장하지 않고 파생 계산**. `meeting_date` 만 있으면 언제든 재계산 가능. 클라이언트/서버 양쪽에서 동일한 함수로 계산 → 단일 진실.

**단, 이미 발동된 마일스톤 (알림 이력)** 은 idempotency 를 위해 별도 감사 테이블에 기록:

```sql
create table if not exists public.meeting_milestone_notifications (
    couple_id  uuid not null references public.couples(id) on delete cascade,
    day_count  int  not null,
    sent_at    timestamptz not null default now(),
    primary key (couple_id, day_count)
);
```

같은 마일스톤이 두 번 알림 나가지 않도록 PK 체크. `couples.meeting_date` 를 변경하면 이 테이블은 그대로 두되, 새 앵커 기준으로 계산해 미발동 마일스톤은 자연스럽게 다시 스케줄.

### 5.3 기존 `couple_anniversaries` 테이블

**향후 후속 이슈에서 재활용 검토**. 이번 스코프에서는 건드리지 않는다 (이미 존재하는 스키마 유지). 관련 UI 진입점은 이 이슈에서 안 만든다.

## 6. 알림

### 6.1 트리거

pg_cron `send_milestone_reminders()` — 매 시간 정각마다 실행:
1. 모든 커플의 `meeting_date + meeting_reminder_hour` 시각에 매칭되는지 확인
2. `today - meeting_date` 가 마일스톤 수 (100 · 200 · … · 365 · 730 · …) 중 하나면 발동 대상
3. `meeting_milestone_notifications` 에 `(couple_id, day_count)` 가 이미 있으면 skip (idempotent)
4. 없으면 커플 양쪽 `user_devices` 로 FCM 발송 + 감사 row insert

### 6.2 문구

```
제목: 💜 우리 만난 지 100일
본문: 오늘로 함께한 지 100일이에요. 축하해요!
```

- 100일/200일 등 단위 카운트: `우리 만난 지 {N}일`
- 1주년/2주년: `우리 만난 지 {N}주년`

### 6.3 발송 대상

**커플 양쪽 모두 알림 수신**. 마일스톤은 관계 공유 이벤트라 `owner_kind` 개념 없음.

### 6.4 Edge Function

`supabase/functions/send-milestone-reminders/index.ts` 신설. pg_cron 이 시간마다 호출. 발송 헬퍼는 `send-schedule-push` 와 공유 (별도 utility 로 리팩터 검토, 이번 스코프에서는 함수별 사본 유지).

## 7. 실시간 반영

- 파트너가 `meeting_date` 변경 → 내 카운터 화면 즉시 갱신
- 기존 `CoupleRealtime.kt` 에 `couples` 채널 이미 존재 → `onCouplesChanged` 훅 재사용
- `MeetingDayCounterViewModel` 이 이 훅에서 `couples.meeting_date` 변화 감지 → state 갱신

## 8. 홈 위젯 (선택 · 후속)

- iOS: `MeetingDayWidget` (small · medium) — 큰 D+N일 텍스트 + 서브에 다음 마일스톤 D-N
- Android: 후속 이슈
- v1.4 에는 앱 안 카운터만. 위젯은 v1.5.

## 9. Edge Case

### 9.1 시간대

- `meeting_date` 는 로컬 date. 서버 계산은 KST 기준 · v1.5 이후 유저별 timezone 옵션 고려
- 알림 시각 (`meeting_reminder_hour`) 도 KST 로 저장

### 9.2 커플 해제 / 재연결

- `unlink_couple` 실행 시 `couples.meeting_date` 는 옛 커플 row 에 그대로 남음 (스케줄 이관과 별개)
- 새 solo 커플의 `meeting_date` 는 NULL 로 초기화
- 재연결 (`join_couple_by_invite`) 시 새 커플의 `meeting_date` 를 유지 (기존 커플이 가진 값 우선)

### 9.3 미래 날짜 방어

- 편집 시트에서 미래 날짜 disabled (UI)
- 서버 CHECK 제약: `meeting_date <= current_date`

### 9.4 매우 오래된 앵커

- 10 년치 마일스톤 (100일 · 1주년 · 1000일 · 3650일 등) 을 앞뒤로 계산해 리스트 렌더
- 스크롤 성능 이슈 없음 (리스트 최대 40 항목 안팎)

## 10. 아키텍처 · 파일 매핑

```
shared/src/commonMain/kotlin/com/hyunjine/linker/
├── feature/meeting/
│   ├── MeetingDayCounterRoute.kt      # Nav 진입점
│   ├── MeetingDayCounterScreen.kt     # 카운터 화면
│   ├── MeetingDayCounterViewModel.kt  # 상태 · 실시간 · 마일스톤 파생
│   ├── MeetingDayEditSheet.kt         # 편집 시트 (wheel picker + 미리보기)
│   ├── MilestoneList.kt               # 지나온 · 앞으로의 리스트
│   └── milestones.kt                  # 마일스톤 계산 함수 (day_counts 배열)
├── data/remote/
│   ├── CouplesRepository.kt           # meeting_date · meeting_reminder_hour CRUD (확장)
│   └── CoupleRealtime.kt              # couples 채널 hookup (기존)
└── designsystem/theme/Color.kt        # ChipMilestoneBg / ChipMilestoneText (신규)

shared/src/commonMain/kotlin/com/hyunjine/linker/feature/main/
└── MainDrawer.kt                       # "우리 만난 날" 진입점 (하트 아이콘 + D+N 서브텍스트)

supabase/
├── migrations/YYYYMMDDHHMMSS__couples_meeting_date.sql
├── migrations/YYYYMMDDHHMMSS__milestone_notifications_audit.sql
└── functions/send-milestone-reminders/
```

## 11. 일정 · 스코프

### v1.4 (이 문서)
- [ ] 마이그레이션 2개 (`couples.meeting_date/reminder_hour` · `meeting_milestone_notifications`)
- [ ] `MeetingDayCounterScreen` · `MeetingDayEditSheet` · `MilestoneList`
- [ ] `MainDrawer` 진입점 (`우리 만난 날` + 하트 아이콘 + D+N 서브)
- [ ] `send-milestone-reminders` Edge Function + pg_cron 매시간 스케줄
- [ ] 캘린더 chip: 앵커일 · 마일스톤 셀 표시
- [ ] `CoupleRealtime` `onCouplesChanged` 재사용해 실시간 반영

### v1.5 후속 (별도 이슈)
- 홈 위젯 (iOS · Android)
- 유저별 timezone
- 마일스톤 커스터마이즈 (특정 카운트 off / 임의 카운트 on)
- 옛 `couple_anniversaries` 스키마 재활용 · 다중 기념일 UX

---

## 참고

- 관련 PR: #316 (드로워 재노출 · 캘린더 chip 최소 표시 · `CalendarEventType.Anniversary` 도입) — 이번 재스코프에 맞춰 `CalendarEventType.Milestone` 으로 rename 필요
- 재사용 파이프라인: `send-schedule-push` (FCM 발송 · 스테일 토큰 정리 헬퍼)
- 색상: 앱 캘린더의 Purple 계열 (`CalendarPurple`) 과 동일 톤
