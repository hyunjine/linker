package com.hyunjine.linker.data.local

import com.hyunjine.linker.platform.LocalStorage

/**
 * Outlook 연동 (#242) 의 기기별 설정. MSAL 계정이 기기마다 따로 저장되므로 같이 로컬에 둔다.
 *
 * 팀 캘린더 (TeamAIVN) 에는 팀원 전체의 `재택(이름) …` 이 섞여 있어, 제목 괄호 안 이름이
 * [teamName] 과 같은 일정만 내 재택으로 미러링한다.
 */
object OutlookLocal {
    private const val K_TEAM_NAME = "outlook.teamName"

    /** 팀 캘린더에 쓰는 내 이름 (예: `양현진`). 미설정이면 null — 동기화하지 않는다. */
    var teamName: String?
        get() = LocalStorage.getString(K_TEAM_NAME, "").ifBlank { null }
        set(value) = LocalStorage.putString(K_TEAM_NAME, value?.trim().orEmpty())
}
