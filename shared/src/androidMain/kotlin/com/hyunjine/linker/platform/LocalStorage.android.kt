package com.hyunjine.linker.platform

import android.content.Context
import android.content.SharedPreferences

private const val PREF_NAME = "linker.prefs"

actual object LocalStorage {
    private var prefs: SharedPreferences? = null

    /**
     * `LinkerApplication.onCreate` 에서 앱 컨텍스트로 한 번 호출.
     * 그 전에 read/write 하면 default 값이 반환되고 write 는 무시됨 (조용히 no-op).
     */
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        prefs?.getBoolean(key, default) ?: default

    actual fun putBoolean(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }
}
