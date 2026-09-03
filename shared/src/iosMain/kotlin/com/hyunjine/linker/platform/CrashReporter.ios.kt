package com.hyunjine.linker.platform

import platform.Foundation.NSLog

/**
 * iOS 구현체. 실제 Crashlytics 호출은 Swift [CrashReporterBridge] handler 가 담당.
 * handler 미세팅이면 NSLog 로만 남기고 조용히 스킵.
 */
actual object CrashReporter {
    actual fun log(message: String) {
        NSLog("[CrashReporter] %@", message)
        CrashReporterBridge.logHandler?.invoke(message)
    }

    actual fun recordException(throwable: Throwable, message: String?) {
        NSLog(
            "[CrashReporter] recordException %@ / %@",
            message ?: "-",
            throwable::class.simpleName ?: "Throwable",
        )
        val handler = CrashReporterBridge.recordHandler ?: return
        val name = throwable::class.simpleName ?: "Throwable"
        val reason = throwable.message ?: throwable.toString()
        handler(message, name, reason)
    }
}
