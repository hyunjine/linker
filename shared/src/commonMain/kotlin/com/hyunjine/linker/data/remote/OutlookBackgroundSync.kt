package com.hyunjine.linker.data.remote

import com.hyunjine.linker.auth.OutlookAuthClient
import com.hyunjine.linker.data.local.OutlookLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 서버 silent push (`data.type = "outlook_sync"`, #420) 로 깨어났을 때 실행하는 Outlook 재택 sync 진입점.
 *
 * Microsoft Graph 변경 알림은 delegated 권한으로 남의 공유 캘린더 (TeamAIVN) 를 구독할 수 없어서,
 * 서버 (`send-outlook-sync-push`) 가 30분마다 연동 유저 기기를 깨우고 기기가 기존
 * [OutlookSyncService.syncRange] 를 그대로 돌린다. UI 없이 실행되므로 Compose 가 필요 없는
 * [OutlookAuthClient] 를 플랫폼이 만들어 넘긴다 (iOS: `OutlookAuthClient()`, Android: application Context).
 *
 * - iOS: `LinkerPushBridge.handleRemoteNotification` → [run] → 끝나면 위젯 갱신 후 completionHandler.
 * - Android: `LinkerFirebaseMessagingService.onMessageReceived` → `OutlookBackgroundSyncAndroid.runSync`.
 *
 * 전체를 [Timeout] 안에 끝낸다 — iOS 는 silent push 에 약 30초만 주고, 넘기면 이후 push 를 throttle 한다.
 */
object OutlookBackgroundSync {

    /** 백그라운드 sync 결과. iOS 는 이 값으로 `UIBackgroundFetchResult` 를 고른다. */
    enum class Outcome {
        /** sync 성공 — 미러가 갱신됐을 수 있다 (newData). */
        Synced,

        /** 할 일 없음 — 로그아웃 · 세션 미준비 · 미연동 · 이름 미설정 · 이미 sync 중 (noData). */
        Skipped,

        /** Graph · DB 오류 또는 시간 초과 (failed). */
        Failed,
    }

    /** 세션 확정 + Graph 조회 + upsert 전체 한도. iOS 30초 안에서 위젯 갱신 여유를 남긴다. */
    private val Timeout: Duration = 20.seconds

    /** 세션 확정 대기 한도. */
    private val SessionWaitTimeout: Duration = 8.seconds

    /** 이 시간 안에 만료될 Supabase 토큰은 sync 전에 갱신 (백그라운드에선 자동 갱신 타이머가 못 돈다). */
    private val TokenExpiryMargin: Duration = 60.seconds

    /**
     * silent push 한 번에 대한 sync 실행. 예외를 던지지 않는다.
     *
     * @param auth UI 없이 만든 Outlook 인증 클라이언트. silent 토큰 ([OutlookAuthClient.accessToken]) 만 쓴다.
     * @return 실행 결과 ([Outcome]).
     */
    suspend fun run(auth: OutlookAuthClient): Outcome {
        // 이름이 없으면 미러 대상이 없다 — 세션 · MSAL 을 건드리기 전에 바로 종료.
        if (OutlookLocal.teamName == null) {
            println("[OutlookBg] 팀 이름 미설정 — skip")
            return Outcome.Skipped
        }
        // foreground sync 가 이미 돌고 있으면 같은 일을 두 번 하지 않는다.
        if (OutlookSyncService.isSyncing) {
            println("[OutlookBg] 이미 sync 중 — skip")
            return Outcome.Skipped
        }
        return try {
            withTimeoutOrNull(Timeout) { runInternal(auth) } ?: run {
                println("[OutlookBg] 시간 초과")
                Outcome.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            println("[OutlookBg] sync 실패: $e")
            Outcome.Failed
        }
    }

    private suspend fun runInternal(auth: OutlookAuthClient): Outcome {
        when (awaitUsableSession(timeout = SessionWaitTimeout, expiryMargin = TokenExpiryMargin, logTag = "OutlookBg")) {
            SessionReadiness.Ready -> Unit
            SessionReadiness.LoggedOut -> {
                println("[OutlookBg] 로그아웃 상태 — skip")
                return Outcome.Skipped
            }
            SessionReadiness.Unavailable -> {
                println("[OutlookBg] 세션 미준비 — skip")
                return Outcome.Skipped
            }
        }
        if (auth.currentAccount() == null) {
            // 이 기기에선 연동 안 됨 (다른 기기에서만 연동했거나 해제됨). 서버 플래그는 유저 단위라 건드리지 않는다.
            println("[OutlookBg] MSAL 계정 없음 — skip")
            return Outcome.Skipped
        }
        val (from, to) = OutlookSyncService.defaultRange()
        val result = OutlookSyncService(auth).syncRange(from, to) ?: run {
            println("[OutlookBg] sync no-op (커플 없음 등)")
            return Outcome.Skipped
        }
        println("[OutlookBg] sync 완료 upserted=${result.upserted} deleted=${result.deleted}")
        return Outcome.Synced
    }
}
