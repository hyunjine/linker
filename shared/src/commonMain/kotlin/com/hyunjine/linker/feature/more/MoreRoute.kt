package com.hyunjine.linker.feature.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 더보기 라우트 (#385). 드로워 하단 "더보기" 탭에서 진입.
 *
 * Outlook 연동 (#242) 상태 · 동작은 앱 전역에서 MSAL 클라이언트를 들고 있는 상위 (App) 가 소유하고
 * 여기로 내려준다 — 백그라운드 sync 와 같은 계정 상태를 공유하기 위해.
 *
 * @param outlook Outlook 연동 상태.
 * @param onBack 뒤로가기.
 * @param onReleaseNotesClick 릴리즈 노트 화면으로.
 * @param onOutlookConnect Outlook 로그인 → 연결.
 * @param onOutlookNameChange 팀 캘린더 이름 저장.
 * @param onOutlookDisconnect Outlook 연결 해제.
 * @param onLogout 로그아웃.
 */
@Composable
fun MoreRoute(
    outlook: OutlookLinkState,
    onBack: () -> Unit,
    onReleaseNotesClick: () -> Unit,
    onOutlookConnect: () -> Unit,
    onOutlookNameChange: (String) -> Unit,
    onOutlookDisconnect: () -> Unit,
    onLogout: () -> Unit,
) {
    val viewModel: MoreViewModel = viewModel { MoreViewModel() }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    MoreScreen(
        ui = ui,
        outlook = outlook,
        onBack = onBack,
        onReleaseNotesClick = onReleaseNotesClick,
        onOutlookConnect = onOutlookConnect,
        onOutlookNameChange = onOutlookNameChange,
        onOutlookDisconnect = onOutlookDisconnect,
        onLogout = onLogout,
    )
}

/**
 * 더보기 "캘린더 연동" 의 Outlook 상태 (#242).
 *
 * @param email 연결된 계정 이메일. null 이면 미연결.
 * @param teamName 팀 캘린더에 쓰는 내 이름. null 이면 미설정 (동기화 안 함).
 * @param busy 로그인 · 동기화 · 해제 진행 중.
 * @param error 마지막 실패 안내 문구. null 이면 없음.
 */
data class OutlookLinkState(
    val email: String? = null,
    val teamName: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)
