# 관리자 푸시 발송 콘솔 (#261)

Compose Multiplatform (Wasm/JS) 로 만드는 관리자 전용 웹. 연결된 계정 목록에서 대상을 선택하거나 "모두 선택" 으로 지정한 뒤 제목·내용을 입력해 FCM 푸시를 보낸다.

> 범위: **로그인 화면 + 발송 화면** 두 화면에 집중. 발송 이력·유저 상세 관리는 후속.

---

## 1. 목적 · 배경

- TestFlight 신 버전 안내, 긴급 공지, 이벤트 알림 등을 앱을 배포하지 않고도 즉시 사용자에게 전달.
- 개발자가 터미널로 직접 FCM curl 을 날리는 현재 임시 방식은 실수 여지가 큼 → 폼으로 안전하게.
- 앱과 톤을 통일하기 위해 Compose Multiplatform 사용, `shared` 모듈의 Supabase 클라이언트와 디자인 토큰 재사용.

## 2. 사용자 흐름

```
[로그인 화면]                       →  [발송 콘솔]
  ├─ 이메일 · 비밀번호                    ├─ 좌: 계정 리스트에서 대상 선택 (체크박스 · 모두 선택)
  ├─ ☐ 자동 로그인 (체크시 브라우저 유지)     ├─ 우: 제목 · 내용 입력 · 대상 요약 확인
  └─ [로그인] → 세션 발급 → 콘솔 진입        └─ 발송 → 완료 스낵바 · 리스트 선택 초기화
```

세션 만료·admin 화이트리스트 실패 시 → 로그인 화면으로 강제 이동.

## 3. 화면 구성

### 3.0 로그인 화면

```
┌────────────────────────────────────────────────────────────┐
│  Linker · 관리자 콘솔                                        │  ← Top bar
├────────────────────────────────────────────────────────────┤
│                                                            │
│              ┌──────────────────────────┐                  │
│              │  관리자 로그인              │                  │
│              │  허용된 계정만 접근할 수 있어요  │                  │
│              │                          │                  │
│              │  이메일                   │                  │
│              │  [ admin@linker.app    ] │                  │
│              │  비밀번호                  │                  │
│              │  [ •••••••••          ] │                  │
│              │                          │                  │
│              │  ☐ 자동 로그인              │                  │
│              │  ┌──────────────────────┐ │                  │
│              │  │        로그인          │ │                  │
│              │  └──────────────────────┘ │                  │
│              └──────────────────────────┘                  │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

| 이름 | 설명 |
|---|---|
| `LoginTopBar` | 콘솔과 동일 브랜딩 (`Linker · 관리자 콘솔`), 우측 액션 없음 |
| `LoginCard` | 화면 중앙 420 wide 카드. 흰 배경, radius 20, 소프트 섀도우 |
| `EmailField` · `PasswordField` | 라벨 + `#F5F5FA` 배경 · radius 12 · 46 tall 인풋 |
| `RememberCheckbox` | 콘솔 "모두 선택" 과 동일한 16×16 체크박스 (`ui/common/Checkbox`) |
| `LoginButton` | 카드 폭 채움 primary pill (46 tall, brand blue, radius 23) |

**폼 유효성** — `email.trim().isNotBlank() && password.isNotEmpty() && !signingIn`.

**에러 표시** — 실패 (자격 오류·admin 아님·네트워크) 시 카드 하단에 인라인 붉은 텍스트. 스낵바는 안 씀 (모달성 없는 정적 메시지가 재시도 흐름에 맞음).

### 3.1 발송 콘솔 레이아웃

```
┌────────────────────────────────────────────────────────────┐
│  Linker Admin · 푸시 발송                        [로그아웃] │  ← Top bar
├──────────────────────────┬─────────────────────────────────┤
│  대상 계정                 │  메시지                        │
│                          │                                 │
│  [🔍 검색]                │  제목                          │
│                          │  ┌───────────────────────────┐  │
│  ☑ 모두 선택 (24)         │  │                          │  │
│  ─────────────────────    │  └───────────────────────────┘  │
│  ☑ 아바타  현진 · iOS      │  내용                          │
│  ☐ 아바타  민교 · Android  │  ┌───────────────────────────┐  │
│  ☐ 아바타  ...            │  │                          │  │
│                          │  │                          │  │
│                          │  │                          │  │
│                          │  └───────────────────────────┘  │
│                          │                                 │
│                          │  선택 3명 · 제목 12자 · 내용 40자 │
│                          │                                 │
│                          │           [발송]                │
└──────────────────────────┴─────────────────────────────────┘
```

### 3.2 컴포넌트

| 이름 | 설명 |
|---|---|
| `TopBar` | 왼쪽 `Linker Admin · 푸시 발송` 브랜딩, 오른쪽 로그아웃 텍스트 버튼 |
| `AccountList` | 스크롤 가능. 상단 검색 입력, 그 아래 `모두 선택` 행, 이후 계정 행 반복 |
| `AccountRow` | 아바타(이니셜) + 닉네임 · 플랫폼(iOS/Android) 배지 + 체크박스. 클릭 시 체크 토글 |
| `MessageForm` | 제목 (1줄 · 최대 40자), 내용 (여러 줄 · 최대 300자), 하단 요약 + 발송 버튼 |
| `SendButton` | Primary pill. 로딩 중 인라인 스피너 (앱 SaveActionPill 스타일 재사용) |
| `Snackbar` | 발송 완료 · 실패 안내 |

### 3.3 상태

- `accounts: List<Account>` — `users` × `user_devices` 조인 결과. `Account` 는 `id · nickname · avatarKind · platforms: Set<Platform>` (iOS/Android/둘 다)
- `search: String` — 클라이언트 필터
- `selectedIds: Set<String>` — 선택된 user id
- `selectAll: Boolean` — 모두 선택 상태 (필터된 리스트 기준)
- `title: String`, `body: String`
- `sending: Boolean` — 발송 중 인라인 스피너
- `snackbar: Snackbar?` — 완료/실패 메시지

### 3.4 유효성

발송 버튼은 아래 조건을 **모두** 만족할 때만 활성화:

- `selectedIds.size > 0`
- `title.trim().isNotEmpty()`
- `body.trim().isNotEmpty()`
- `!sending`

## 4. 데이터 · API

### 4.1 계정 조회

FCM 토큰은 `users` 가 아니라 `user_devices` 에 있음 (`supabase/migrations/20260831130000__user_devices_fcm.sql`). 한 유저가 여러 기기 (iOS + Android) 를 가질 수 있으므로 embed 로 device 리스트를 함께 받고, 클라이언트에서 `platforms: Set<Platform>` 로 접어준다.

```kotlin
Supabase.from("users")
    .select(Columns.raw("id, nickname, profile_image_url, user_devices!inner(platform)")) {
        // !inner → device 가 하나 이상 있는 유저만 (푸시 가능 대상)
        order("nickname", Order.ASCENDING)
    }
    .decodeList<AdminUserRowRaw>()
    .map { row ->
        Account(
            id = row.id,
            nickname = row.nickname ?: "",
            profileImageUrl = row.profileImageUrl,
            platforms = row.userDevices.map { Platform.of(it.platform) }.toSet(),
        )
    }

@Serializable
data class AdminUserRowRaw(
    val id: String,
    val nickname: String?,
    @SerialName("profile_image_url") val profileImageUrl: String?,
    @SerialName("user_devices") val userDevices: List<Device>,
) {
    @Serializable data class Device(val platform: String)
}
```

`Account.profileImageUrl` 이 null 이면 이니셜 (닉네임 첫 글자) 을 렌더 · 있으면 이미지 렌더. 앱의 `AsyncImage` 폴백과 같은 규칙.

배지 표시: `platforms` 를 순회하면서 각 플랫폼별 배지를 나열. iOS + Android 둘 다면 두 배지가 나란히 보임 (좌 iOS · 우 Android, 텍스트 색은 앱과 동일 톤).

### 4.2 발송

Supabase Edge Function `send-announcement` 로 위임 (FCM 서비스 계정 키는 함수 안에만 존재, 브라우저 노출 X):

- **Request**
  ```json
  {
    "title": "새 버전이 배포됐어요",
    "body": "TestFlight 에서 v1.4.0 을 확인해 주세요.",
    "userIds": ["uuid1", "uuid2"]   // 또는 "all"
  }
  ```
- **Response**
  ```json
  { "sent": 22, "failed": 2, "errors": [{ "userId": "uuid", "reason": "invalid_token" }] }
  ```

브라우저에서는 `supabase.functions.invoke("send-announcement", body)` 로 호출.

### 4.3 실패 처리

- 부분 실패 시 스낵바에 `20/22 발송 성공, 2건 실패` 표시.
- 실패 상세는 발송 이력 페이지(후속)에서 확인. 지금은 로그로만.

## 5. 모듈 · 기술 스택

- 새 모듈 `:adminWeb` — Compose Multiplatform, target `wasmJs { browser() }`.
- **`:shared` 와 독립.** `:shared` 는 Android · iOS 타깃만 갖고 있고 여러 platform 전용 `expect`/`actual` (FcmTokenBridge · WidgetRefresh · MSAL Android-only · ktor darwin/okhttp 등) 을 품고 있어 wasmJs 로 데려오는 비용이 큼. 관리자 콘솔에서 재사용 필요한 표면 (Supabase 클라이언트 초기화, Color 몇 개, 계정 스키마 정도) 은 좁으므로 **`:adminWeb` 안에 자체 구현** 으로 결정 (2026-09-17). 컬러 정의는 앱과 두 곳에 존재하나 수정 빈도가 낮아 감수. 재사용 표면이 커지면 별도 `:sharedCore` 로 분리하는 후속 이슈 검토.
- Router: 로그인 · 콘솔 두 화면. Voyager (또는 자체 route 관리).
- 스타일: Pretendard + 앱과 동일한 Color 팔레트 (`ui/theme/Color.kt` 를 참고하여 필요한 상수만 `:adminWeb` 에 카피).
- 호스팅: GitHub Pages (`gh-pages` 브랜치 자동 배포 워크플로) — #276 참조.

## 6. 인증 · 로그인 · 세션

### 6.1 흐름

1. 브라우저 진입 → 세션 확인 (`supabase.auth.getSession()`).
2. 세션 있음 + `admin_uids` 화이트리스트 통과 → 발송 콘솔로 바로.
3. 세션 없음 or 화이트리스트 미통과 → **로그인 화면** (`3.0` 참조).
4. 로그인 성공 → 화이트리스트 재검증. 통과 시 콘솔로, 실패 시 카드 하단에 "관리자 권한이 없는 계정입니다" 인라인 에러.
5. Edge Function 안에서도 `admin_uids` 화이트리스트를 재검증 → 브라우저 우회 방지.

### 6.2 자동 로그인 (체크박스)

`supabase-js` 의 세션 저장 위치를 `storage` 옵션으로 제어. 사용자가 "자동 로그인" 을 켜고 껐을 때 저장소가 달라진다.

| 상태 | `storage` | 결과 |
|---|---|---|
| ☑ 자동 로그인 | `localStorage` | 브라우저 종료 후에도 세션 유지. 재접속 시 자동 로그인. |
| ☐ 해제 | `sessionStorage` | 탭/브라우저 닫으면 세션 소멸 → 다음 접속 시 재로그인. |

로그인 시점에 체크 상태에 따라 클라이언트를 다르게 초기화하는 대신, **로그인 성공 직후 `sessionStorage` ↔ `localStorage` 로 세션을 옮기는** 얇은 헬퍼로 구현 (단일 supabase-js 인스턴스 유지).

```kotlin
// 로그인 성공 후
if (rememberMe) {
    // 이미 localStorage 에 저장돼 있으므로 별도 처리 불필요 (기본값)
} else {
    // 세션을 sessionStorage 로 옮기고 localStorage 항목 제거
    moveSessionTo(sessionStorage)
}
```

`persistSession: false` (메모리에만) 는 새로고침만 해도 로그아웃되어 실사용 불가 → 채택 안 함.

### 6.3 세션 만료

- supabase-js 가 refresh token 으로 자동 갱신. 갱신 실패 (invalid_grant · 계정 삭제 등) 시 `onAuthStateChange` 로 `SIGNED_OUT` → 로그인 화면 이동 + 스낵바 "세션이 만료되어 다시 로그인해주세요".
- 자동 로그인 OFF 상태에서 브라우저 종료 → 다음 접속 시 세션 자체가 없으므로 자연스럽게 로그인 화면.

### 6.4 로그아웃

콘솔 탑바 우측 "로그아웃" 탭 → `supabase.auth.signOut()` → `localStorage`/`sessionStorage` 둘 다 정리 → 로그인 화면. 확인 다이얼로그 없음 (파괴적 액션 아님).

## 7. 발송 UX 세부

- 발송 버튼 클릭 즉시 로컬 낙관 처리 없이 **응답을 기다림** (임의 broadcast 는 파괴적이라 확인이 필요).
- 완료 스낵바 뜨면 폼 초기화 (`title = ""`, `body = ""`, `selectedIds = emptySet()`).
- 실패 스낵바는 폼 그대로 유지 → 재시도 가능.
- 대상 요약 문구 예: `선택 3명 · 제목 12자 · 내용 40자` (현재 카운트 실시간 갱신, 초과 시 빨강).

## 8. 후속 (본 문서 밖)

- 발송 이력 페이지 (누가 언제 무슨 메시지를 몇 명에게 보냈나).
- 이미지 · 딥링크 첨부 옵션.
- 예약 발송 (cron 스케줄).
- CI · gh-pages 배포 워크플로.
- 비밀번호 재설정 · 관리자 초대 (`admin_uids` 관리 UI).

## 9. 열린 결정

- **모두 선택 vs 대상 없음** — 검색 필터가 걸린 상태에서 "모두 선택" 은 **필터된 결과 전체** 를 의미하는 것으로 함 (UX 일관성).
- **플랫폼 배지** — 한 유저가 iOS · Android 기기를 모두 가진 경우 **두 배지 모두 표시** (`iOS` `Android` 두 pill 나란히). 어느 하나로 대표시키지 않음 → 사용자가 어떤 기기까지 푸시가 도달할지 명확히 알 수 있음.
- **플랫폼별 필터** — 필요하면 상단에 iOS/Android 세그먼트 추가 (지금은 생략).
- **테스트 발송** — 관리자 본인에게만 보내는 dry-run 버튼 필요 여부 논의.
