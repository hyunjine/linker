package com.hyunjine.linker

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.hyunjine.linker.auth.initKakaoSdk
import com.hyunjine.linker.data.remote.SupabaseProvider
import com.hyunjine.linker.platform.CrashReporter
import com.hyunjine.linker.platform.DebugConfig
import com.hyunjine.linker.platform.FcmTokenBridge
import com.hyunjine.linker.platform.LocalStorage

/**
 * 앱 프로세스 시작 시 1회 실행. 카카오 SDK · Supabase 클라이언트 초기화 담당.
 * 향후 로거·크래시 리포터·DI 초기화가 여기에 붙는다.
 */
class LinkerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // debuggable 플래그로 debug 빌드 판정 (BuildConfig 활성화 없이도 됨).
        DebugConfig.enabled = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // 로컬 저장소 (SharedPreferences 래퍼) 초기화 — 이후 shared 코드에서 sync read/write 가능.
        LocalStorage.init(this)
        initKakaoSdk(this)
        // lazy 지연 초기화 트리거 (링킹/설정 문제를 앱 시작 시 조기 감지).
        Log.d("Linker", "Supabase project = ${SupabaseProvider.warmUp()}")

        // FCM 토큰 획득 시도. 이미 캐시된 게 있으면 즉시 반환됨. onNewToken 은 rotation 시만 fire
        // 하므로 앱 시작 시 한 번 pull 로 upsert 안전벨트. 로그인 안 됐으면 upsert 는 no-op.
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("Linker", "FCM initial token=${token.take(12)}…")
                FcmTokenBridge.onTokenRefreshedAsync(token, platform = "android")
            }
            .addOnFailureListener { Log.w("Linker", "FCM token 획득 실패", it) }

        // TODO(#176): 배포 전 제거. Crashlytics 연동 확인용 non-fatal.
        // 앱 시작 후 1~2분 뒤 Crashlytics 콘솔 → Non-fatal issues 에 "AppStartupSmokeTest" 이 뜨면 OK.
        CrashReporter.recordException(
            RuntimeException("AppStartupSmokeTest"),
            "Android 앱 시작 시 Crashlytics 연동 확인용 non-fatal",
        )
    }
}
