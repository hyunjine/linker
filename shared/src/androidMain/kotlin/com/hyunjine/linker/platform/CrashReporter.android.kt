package com.hyunjine.linker.platform

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Android 구현체. Firebase Crashlytics SDK 로 직접 위임.
 * [FirebaseCrashlytics] 는 Application.onCreate 시점에 자동 초기화된다 (google-services 플러그인).
 */
actual object CrashReporter {
    actual fun log(message: String) {
        Log.i(TAG, message)
        runCatching { FirebaseCrashlytics.getInstance().log(message) }
    }

    actual fun recordException(throwable: Throwable, message: String?) {
        Log.w(TAG, message ?: "recordException", throwable)
        runCatching {
            val c = FirebaseCrashlytics.getInstance()
            if (message != null) c.log(message)
            c.recordException(throwable)
        }
    }
}

private const val TAG = "CrashReporter"
