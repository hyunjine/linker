package com.hyunjine.linker.feature.couple

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyunjine.linker.platform.rememberCopyToClipboard
import com.hyunjine.linker.platform.rememberShareText

/**
 * 커플 연결 chooser 라우트. VM 이 파트너 조인 여부를 미리 조회해 Screen state 에 반영.
 * Paired 면 옵션 카드를 감추고 안내 UI 로 대체.
 */
@Composable
fun CoupleLinkRoute(
    onBack: () -> Unit,
    onCreateInvite: () -> Unit,
    onEnterPartnerCode: () -> Unit,
    onUnlinked: () -> Unit = {},
) {
    val viewModel: CoupleLinkViewModel = viewModel { CoupleLinkViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CoupleLinkScreen(
        state = state,
        onBack = onBack,
        onCreateInvite = onCreateInvite,
        onEnterPartnerCode = onEnterPartnerCode,
        onUnlink = { viewModel.unlink(onUnlinked) },
    )
}

/** 초대코드 발급 · 공유 · 이미 파트너 있음 안내 화면 라우트. */
@Composable
fun CoupleInviteCodeRoute(onBack: () -> Unit) {
    val viewModel: CoupleInviteCodeViewModel = viewModel { CoupleInviteCodeViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val copyToClipboard = rememberCopyToClipboard()
    val shareText = rememberShareText()
    CoupleInviteCodeScreen(
        state = state,
        onBack = onBack,
        onCopy = {
            val code = (state as? InviteCodeUiState.Solo)?.code ?: return@CoupleInviteCodeScreen
            copyToClipboard(code)
            println("[Couple] 클립보드 복사: $code")
        },
        onShare = {
            val code = (state as? InviteCodeUiState.Solo)?.code ?: return@CoupleInviteCodeScreen
            shareText("링커 초대코드: $code")
            println("[Couple] 공유 시트 오픈: $code")
        },
    )
}

/** 상대 코드 입력 → join 라우트. 성공 시 [onJoined] 호출 (App 이 홈으로 이동). */
@Composable
fun CoupleJoinRoute(
    onBack: () -> Unit,
    onJoined: () -> Unit,
) {
    val viewModel: CoupleJoinViewModel = viewModel { CoupleJoinViewModel() }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    CoupleJoinScreen(
        linking = ui.linking,
        onBack = onBack,
        onLink = { code -> viewModel.link(code, onJoined) },
    )
}
