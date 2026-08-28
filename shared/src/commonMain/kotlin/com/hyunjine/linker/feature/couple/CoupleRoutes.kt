package com.hyunjine.linker.feature.couple

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyunjine.linker.platform.rememberCopyToClipboard
import com.hyunjine.linker.platform.rememberShareText

/** 초대코드 발급 · 공유 화면 라우트. */
@Composable
fun CoupleInviteCodeRoute(onBack: () -> Unit) {
    val viewModel: CoupleInviteCodeViewModel = viewModel { CoupleInviteCodeViewModel() }
    val myCode by viewModel.myCode.collectAsStateWithLifecycle()
    val copyToClipboard = rememberCopyToClipboard()
    val shareText = rememberShareText()
    CoupleInviteCodeScreen(
        myCode = myCode,
        onBack = onBack,
        onCopy = {
            val code = myCode ?: return@CoupleInviteCodeScreen
            copyToClipboard(code)
            println("[Couple] 클립보드 복사: $code")
        },
        onShare = {
            val code = myCode ?: return@CoupleInviteCodeScreen
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
