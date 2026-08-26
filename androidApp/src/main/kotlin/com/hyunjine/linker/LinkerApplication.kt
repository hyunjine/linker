package com.hyunjine.linker

import android.app.Application
import android.util.Log
import com.hyunjine.linker.auth.initKakaoSdk
import com.hyunjine.linker.data.remote.SupabaseProvider

/**
 * 앱 프로세스 시작 시 1회 실행. 카카오 SDK · Supabase 클라이언트 초기화 담당.
 * 향후 로거·크래시 리포터·DI 초기화가 여기에 붙는다.
 */
class LinkerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKakaoSdk(this)
        // lazy 지연 초기화 트리거 (링킹/설정 문제를 앱 시작 시 조기 감지).
        Log.d("Linker", "Supabase project = ${SupabaseProvider.warmUp()}")
    }
}
