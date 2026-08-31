package com.hyunjine.linker.platform

import com.hyunjine.linker.data.remote.UserDevicesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// FCM 토큰 라이프사이클 브리지.
// 네이티브 (Android FirebaseMessagingService.onNewToken · iOS Messaging.messaging didReceiveRegistrationToken)
// 에서 새 토큰을 받으면 이 오브젝트로 넘겨 Supabase user_devices 에 upsert.
// commonMain 에 두어 shared 안에서 Repository 를 바로 호출.
object FcmTokenBridge {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 토큰 refresh 시 네이티브가 호출. 로그인 여부는 Repository 가 auth.uid() 로 판정.
    // 로그인 안 됐으면 no-op 하고 나중에 registerCurrentDeviceIfPossible 로 재시도.
    suspend fun onTokenRefreshed(token: String, platform: String) {
        UserDevicesRepository.upsertMyDevice(token = token, platform = platform)
    }

    // suspend 안 되는 컨텍스트 (Android Service) 용 fire-and-forget 래퍼.
    fun onTokenRefreshedAsync(token: String, platform: String) {
        scope.launch { runCatching { onTokenRefreshed(token, platform) } }
    }
}
