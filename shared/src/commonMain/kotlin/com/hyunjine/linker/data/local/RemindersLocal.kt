package com.hyunjine.linker.data.local

import com.hyunjine.linker.platform.LocalStorage

/**
 * ReminderScheduler 가 마지막에 예약한 알림 id 집합을 로컬 (SharedPreferences · NSUserDefaults) 에
 * 캐시. OS 는 pending 알림 목록을 조회할 API 가 부분적으로만 노출돼 있어 (Android AlarmManager 는
 * enumerate 불가) 우리가 직접 트래킹하는 편이 안전.
 *
 * 저장 형식: 세미콜론 구분 문자열. 스케줄 id (UUID) 는 세미콜론 미포함이라 안전.
 */
object RemindersLocal {
    private const val K_IDS = "reminders.pendingIds"
    private const val SEP = ";"

    fun loadIds(): Set<String> {
        val raw = LocalStorage.getString(K_IDS, default = "")
        if (raw.isEmpty()) return emptySet()
        return raw.split(SEP).filter { it.isNotEmpty() }.toSet()
    }

    fun saveIds(ids: Collection<String>) {
        LocalStorage.putString(K_IDS, ids.joinToString(SEP))
    }

    fun remove(id: String) {
        val next = loadIds().minus(id)
        saveIds(next)
    }
}
