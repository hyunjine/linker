package com.hyunjine.linker.platform

/**
 * 앱 전역 key-value 로컬 저장소. 프로세스 재시작 후에도 유지 (SharedPreferences · NSUserDefaults).
 *
 * 동기 API 만 노출 — 값이 매우 작아 코루틴 · 백그라운드 dispatch 오버헤드가 저장 자체보다 큼.
 * 큰 blob 이나 잦은 write 가 생기면 그때 async 로 리팩터.
 *
 * Android 는 [init] 을 LinkerApplication.onCreate 에서 반드시 한 번 호출해야 함.
 * iOS 는 NSUserDefaults 가 자체 초기화되므로 init 불필요.
 */
expect object LocalStorage {
    /** default 를 넘겨 저장된 값이 없으면 반환. */
    fun getBoolean(key: String, default: Boolean): Boolean

    /** 즉시 반영 (Android 는 SharedPreferences.Editor.apply — 실제 write 는 비동기지만 read 는 즉시 새 값). */
    fun putBoolean(key: String, value: Boolean)

    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
}
