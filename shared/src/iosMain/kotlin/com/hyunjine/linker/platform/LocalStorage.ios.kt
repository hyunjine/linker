package com.hyunjine.linker.platform

import platform.Foundation.NSUserDefaults

actual object LocalStorage {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        // objectForKey 로 존재 여부를 먼저 확인 — boolForKey 는 없으면 무조건 false 반환해
        // 실제 default 를 못 씀.
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    actual fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }

    actual fun getString(key: String, default: String): String =
        defaults.stringForKey(key) ?: default

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}
