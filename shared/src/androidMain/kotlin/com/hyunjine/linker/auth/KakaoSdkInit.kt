package com.hyunjine.linker.auth

import android.content.Context
import com.hyunjine.linker.data.Secrets
import com.kakao.sdk.common.KakaoSdk

/**
 * 앱 프로세스 시작 시 1회 호출. [LinkerApplication.onCreate] 등에서 호출한다.
 * Kakao SDK 는 [com.kakao.sdk.user.UserApiClient] 최초 사용 전 반드시 초기화되어야 한다.
 *
 * 키는 `local.properties` → `shared/build.gradle.kts` 의 generateSecrets 태스크가 만든
 * [Secrets.KakaoNativeAppKey] 로 주입. androidApp 이 카카오 SDK 나 Secrets 를 직접 참조하지 않도록
 * 이 함수 하나만 노출한다.
 */
fun initKakaoSdk(context: Context) {
    KakaoSdk.init(context, Secrets.KakaoNativeAppKey)
}
