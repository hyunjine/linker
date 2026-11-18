package com.hyunjine.linker.data.remote

import android.content.Context
import com.hyunjine.linker.auth.OutlookAuthClient
import kotlinx.coroutines.Dispatchers

/**
 * Android 의 Outlook 백그라운드 sync 진입점 (#420). `LinkerFirebaseMessagingService.onMessageReceived`
 * 가 `data.type = "outlook_sync"` 메시지를 받으면 호출한다.
 *
 * `onMessageReceived` 는 FCM 이 메인이 아닌 작업 스레드에서 부르고, 반환할 때까지 서비스를 살려 둔다
 * (약 20초 한도). 그래서 WorkManager (의존성 없음) 대신 그 스레드를 `runBlocking` 으로 잡고
 * [OutlookBackgroundSync.run] (자체 20초 timeout) 을 끝까지 돌린다 — 가장 단순하고, 프로세스가
 * 중간에 정리될 위험이 없다.
 *
 * MSAL silent 토큰 ([OutlookAuthClient.accessToken]) 은 Activity 없이 application Context 만으로 동작한다
 * (대화형 [OutlookAuthClient.login] 만 Activity 필요).
 */
object OutlookBackgroundSyncAndroid {

    /**
     * 현재 스레드를 막고 백그라운드 sync 실행. 메인 스레드에서 부르면 안 된다.
     *
     * @param context 아무 Context — 내부에서 applicationContext 로 바꿔 쓴다.
     * @return 실행 결과.
     */
    fun runSync(context: Context): OutlookBackgroundSync.Outcome =
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            OutlookBackgroundSync.run(OutlookAuthClient(context.applicationContext))
        }
}
