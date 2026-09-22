package com.hyunjine.linker.adminweb.data

/**
 * 관리자 콘솔이 인식하는 FCM 대상 플랫폼.
 *
 * `public.user_devices.platform` 컬럼 (`'ios' | 'android'` CHECK 제약) 과 1:1 로 매핑되며,
 * 한 유저가 iOS · Android 기기를 모두 갖고 있는 경우 [Account.platforms] 에 두 값이 함께 담긴다.
 * — docs/261-admin-push-console.md §9 (플랫폼 배지 결정).
 */
enum class Platform {
    iOS,
    Android;

    companion object {
        /**
         * DB 문자열 → [Platform] 매핑. 알려지지 않은 값이 오면 즉시 실패시켜
         * 스키마 변경 (신규 플랫폼 추가) 을 놓치지 않도록 한다.
         *
         * @param raw `user_devices.platform` 원본 문자열 (`"ios"` · `"android"`).
         */
        fun of(raw: String): Platform = when (raw) {
            "ios" -> iOS
            "android" -> Android
            else -> throw IllegalArgumentException("unknown platform: $raw")
        }
    }
}
