package com.hyunjine.linker

import android.app.Application
import com.hyunjine.linker.auth.initKakaoSdk

/**
 * 앱 프로세스 시작 시 1회 실행. 지금은 카카오 SDK 초기화만 담당.
 * 향후 로거·크래시 리포터·DI 초기화가 여기에 붙는다.
 */
class LinkerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKakaoSdk(this)
    }
}
