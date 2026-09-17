package com.hyunjine.linker.adminweb.auth

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.w3c.dom.Storage

/**
 * 브라우저 저장소 종류.
 *
 * `LOCAL` 은 `localStorage` — 브라우저를 닫아도 세션 유지 (자동 로그인 ON).
 * `SESSION` 은 `sessionStorage` — 탭/브라우저를 닫으면 소멸 (자동 로그인 OFF).
 */
enum class BrowserStorageKind {
    LOCAL,
    SESSION,
}

/**
 * `localStorage` ↔ `sessionStorage` 사이에서 세션을 옮길 수 있는 [SessionManager] 구현.
 *
 * supabase-kt 기본 세션 매니저는 multiplatform-settings 를 통해 `localStorage` 를 사용한다.
 * 하지만 자동 로그인 체크박스에 따라 `sessionStorage` 로 옮겨야 하므로, 저장소를 직접 다루는
 * 얇은 커스텀 매니저를 만들어 supabase-kt 초기화 시점에 주입한다.
 *
 * 핵심 규약:
 * - 어느 순간이든 활성 저장소는 하나 — 다른 쪽에는 세션 흔적을 남기지 않는다 ([writeTo] 는
 *   저장 시 항상 반대편도 클리어).
 * - [switchTo] 는 현재 세션을 그대로 반대편으로 이동시키고 원래 저장소를 비운다. 세션이
 *   없는 상태에서 호출해도 안전 (양쪽 모두 비운 상태로 정리만 수행).
 * - JSON 직렬화는 필드 추가에 관대하도록 `ignoreUnknownKeys = true`. supabase-kt 가 마이너
 *   업데이트로 새 필드를 추가해도 기존 저장 값을 읽을 때 깨지지 않는다.
 *
 * @param initialStorage 초기 활성 저장소. 첫 진입 시 이미 저장된 세션이 없다면 사실상
 *   무의미하지만, 이후 [saveSession] 이 발생했을 때 어느 쪽으로 쓸지를 결정한다.
 */
class BrowserStorageSessionManager(
    initialStorage: BrowserStorageKind = BrowserStorageKind.LOCAL,
) : SessionManager {

    /** 현재 활성 저장소. UI 스레드에서만 갱신되므로 volatile 은 불필요. */
    private var activeKind: BrowserStorageKind = initialStorage

    /**
     * 저장/삭제/이동을 동시성 없이 직렬화. supabase-kt 콜백과 로그인/로그아웃 흐름이
     * 동시에 스토리지에 접근할 여지를 원천 차단.
     */
    private val mutex = Mutex()

    /** 현재 활성 저장소 종류. UI (자동 로그인 체크박스 초기 상태 등) 에서 조회. */
    val currentKind: BrowserStorageKind get() = activeKind

    override suspend fun saveSession(session: UserSession) {
        mutex.withLock {
            val serialized = json.encodeToString(UserSession.serializer(), session)
            writeTo(activeKind, serialized)
        }
    }

    override suspend fun loadSession(): UserSession {
        return loadSessionOrNull() ?: throw NoSessionInBrowserStorageException()
    }

    override suspend fun loadSessionOrNull(): UserSession? {
        mutex.withLock {
            // localStorage 를 먼저 확인해 자동 로그인 세션을 우선 복원. 없으면 sessionStorage.
            // 부트 시점에는 activeKind 값이 아직 사용자 의도를 반영하지 못하므로 두 곳을 모두 살핀다.
            val fromLocal = readFrom(BrowserStorageKind.LOCAL)
            if (fromLocal != null) {
                activeKind = BrowserStorageKind.LOCAL
                return decode(fromLocal)
            }
            val fromSession = readFrom(BrowserStorageKind.SESSION)
            if (fromSession != null) {
                activeKind = BrowserStorageKind.SESSION
                return decode(fromSession)
            }
            return null
        }
    }

    override suspend fun deleteSession() {
        mutex.withLock {
            clear(BrowserStorageKind.LOCAL)
            clear(BrowserStorageKind.SESSION)
        }
    }

    /**
     * 활성 저장소를 [target] 으로 전환한다. 저장된 세션이 있으면 [target] 으로 이동시키고
     * 원래 저장소는 비운다. 저장된 세션이 없으면 활성 표식만 업데이트한다.
     */
    suspend fun switchTo(target: BrowserStorageKind) {
        mutex.withLock {
            if (activeKind == target) {
                // 그래도 반대편에 흔적이 남아 있을 수 있으니 정리 (idempotent).
                val other = if (target == BrowserStorageKind.LOCAL) BrowserStorageKind.SESSION
                else BrowserStorageKind.LOCAL
                clear(other)
                return
            }
            val currentValue = readFrom(activeKind)
            if (currentValue != null) {
                writeTo(target, currentValue)
                clear(activeKind)
            } else {
                // 세션이 없다면 두 저장소 모두 비워두는 편이 안전.
                clear(BrowserStorageKind.LOCAL)
                clear(BrowserStorageKind.SESSION)
            }
            activeKind = target
        }
    }

    private fun decode(raw: String): UserSession? =
        runCatching { json.decodeFromString(UserSession.serializer(), raw) }
            .onFailure { println("[AdminAuth] 세션 JSON 파싱 실패 → 무시: $it") }
            .getOrNull()

    private fun writeTo(kind: BrowserStorageKind, value: String) {
        storageOf(kind).setItem(STORAGE_KEY, value)
        // 활성 저장소로만 쓰고 나머지는 비워 흔적을 남기지 않는다.
        val other = if (kind == BrowserStorageKind.LOCAL) BrowserStorageKind.SESSION
        else BrowserStorageKind.LOCAL
        clear(other)
    }

    private fun readFrom(kind: BrowserStorageKind): String? =
        storageOf(kind).getItem(STORAGE_KEY)?.takeIf { it.isNotBlank() }

    private fun clear(kind: BrowserStorageKind) {
        storageOf(kind).removeItem(STORAGE_KEY)
    }

    private fun storageOf(kind: BrowserStorageKind): Storage = when (kind) {
        BrowserStorageKind.LOCAL -> localStorage
        BrowserStorageKind.SESSION -> sessionStorage
    }

    companion object {
        /**
         * 브라우저 저장소에 세션을 담을 때 사용할 키 이름.
         *
         * `signOut` 후 이중 안전장치로 저장소를 직접 청소할 때도 이 상수를 재사용한다.
         */
        internal const val STORAGE_KEY = "linker.adminweb.supabase.session"

        /** 관대한 파싱 (`ignoreUnknownKeys`) 으로 라이브러리 마이너 업데이트에 강인. */
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/** [BrowserStorageSessionManager.loadSession] 이 세션을 찾지 못했을 때 던지는 예외. */
class NoSessionInBrowserStorageException :
    RuntimeException("No admin session in localStorage or sessionStorage")
