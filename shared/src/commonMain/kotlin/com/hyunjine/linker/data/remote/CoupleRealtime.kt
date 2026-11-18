package com.hyunjine.linker.data.remote

import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 커플 스코프 postgres_changes 구독 유틸.
 *
 * ViewModel 이 [subscribe] 로 채널을 열고, 필요한 콜백 (schedules · anniversaries · profile) 을 넘긴다.
 * 반환된 [Job] 을 cancel 하면 채널도 자동 unsubscribe 되도록 구성.
 *
 * 구독 대상:
 *  - `schedules`, `schedule_repeat_rules` — 내 couple 필터 (RLS 도 걸리지만 명시적으로 filter 도 걺)
 *  - `couple_anniversaries` — 내 couple 필터
 *  - `couple_photos` — 내 couple 필터 (파트너가 올리거나 지운 사진 즉시 반영). 마이그레이션 적용
 *    순서와 무관하게 기존 구독이 안전하도록 **별도 채널** 로 연다.
 *  - `users` — 파트너 프로필 변경 (내 프로필은 로컬에서 이미 즉시 반영, 파트너만 관심)
 *  - `couple_members` — 파트너 join · 이탈
 *
 * WebSocket 은 앱이 foreground 인 동안만 활성 (Supabase-kt 기본). 백그라운드에서 오는 이벤트는
 * 앱 재개 시 `refreshSchedules` 등이 fetch 로 커버.
 */
class CoupleRealtimeSubscription(
    val onSchedulesChanged: () -> Unit = {},
    val onAnniversariesChanged: () -> Unit = {},
    val onPhotosChanged: () -> Unit = {},
    val onCoupleChanged: () -> Unit = {},
    val onPartnerProfileChanged: () -> Unit = {},
)

/**
 * 커플 채널을 열고 [subscription] 콜백을 연결한다.
 *
 * 구독 준비 중 실패 (재진입 직후 끊긴 연결로 `myCoupleId()` 조회 실패 · 채널 subscribe 실패 등) 는 로그만 남기고
 * 구독을 포기한다 (#422). 예외가 `launch` 밖으로 새면 Kotlin/Native 에선 처리되지 않은 예외로 앱이 종료된다.
 * 다음 재진입 (`onResume` → `startRealtime`) 때 다시 시도.
 *
 * @param subscription 테이블별 변경 콜백 묶음.
 * @return 실행 중인 구독 [Job]. VM 이 clear 될 때 cancel 해야 채널 leak 방지.
 */
fun CoroutineScope.subscribeCoupleRealtime(subscription: CoupleRealtimeSubscription): Job = launch(RealtimeExceptionHandler) {
    try {
        // coroutineScope 로 감싸야 launchIn 으로 띄운 하위 flow 의 실패도 여기서 잡힌다.
        coroutineScope { subscribeCoupleRealtimeBody(subscription) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        println("[Realtime] 구독 실패 — 다음 재진입 때 재시도: $e")
    }
}

/** try/catch 를 빠져나간 예외 (방어용) 도 앱을 죽이지 않도록 로그만 남긴다. */
private val RealtimeExceptionHandler = CoroutineExceptionHandler { _, e ->
    println("[Realtime] 처리되지 않은 예외: $e")
}

/**
 * [subscribeCoupleRealtime] 본문. cancel 될 때까지 suspend 하며 채널을 유지한다.
 *
 * @param subscription 테이블별 변경 콜백 묶음.
 */
private suspend fun CoroutineScope.subscribeCoupleRealtimeBody(subscription: CoupleRealtimeSubscription) {
    val coupleId = SchedulesRepository.myCoupleId() ?: run {
        println("[Realtime] couple 없음 — 구독 스킵")
        return
    }
    val client = SupabaseProvider.client
    val channel: RealtimeChannel = client.channel("couple:$coupleId")

    // schedules / schedule_repeat_rules 두 테이블을 하나의 채널에서 각각 구독. onEach 에서
    // 어떤 이벤트든 "스케줄이 뭔가 바뀌었다" 로 축약해서 refresh 콜백 하나로 처리 (MVP).
    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "schedules"
        filter("couple_id", FilterOperator.EQ, coupleId)
    }.onEach {
        println("[Realtime] schedules change: ${it::class.simpleName}")
        subscription.onSchedulesChanged()
    }.launchIn(this)

    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "schedule_repeat_rules"
        // schedule_repeat_rules 는 couple_id 컬럼이 없어서 서버 side 필터 못 검. RLS 로 이미 걸러짐.
    }.onEach {
        println("[Realtime] schedule_repeat_rules change: ${it::class.simpleName}")
        subscription.onSchedulesChanged()
    }.launchIn(this)

    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "couple_anniversaries"
        filter("couple_id", FilterOperator.EQ, coupleId)
    }.onEach {
        println("[Realtime] anniversaries change: ${it::class.simpleName}")
        subscription.onAnniversariesChanged()
    }.launchIn(this)

    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "couple_members"
        filter("couple_id", FilterOperator.EQ, coupleId)
    }.onEach {
        println("[Realtime] couple_members change: ${it::class.simpleName}")
        subscription.onCoupleChanged()
    }.launchIn(this)

    // users 는 내 자신 · 파트너 둘 다 UPDATE 를 받게 됨. 서버 필터는 users.id in (내 couple 멤버) 로
    // 간단히 걸기 어려워 (postgres_changes 는 컬럼-값 필터만 지원) RLS 필터에 맡기고 클라이언트에서 콜백.
    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "users"
    }.onEach {
        println("[Realtime] users change: ${it::class.simpleName}")
        subscription.onPartnerProfileChanged()
    }.launchIn(this)

    channel.subscribe()
    println("[Realtime] couple:$coupleId 채널 subscribe 완료")

    // couple_photos 는 **별도 채널** 로 분리한다.
    //
    // postgres_changes 바인딩은 채널 단위로 한 번에 서버에 등록되므로, 아직 publication 에 없는
    // 테이블 (= 사진첩 마이그레이션 미적용 환경) 이 하나라도 끼면 그 채널의 subscribe 가 통째로
    // 실패해 schedules · anniversaries 구독까지 같이 죽는다. 앱 배포와 마이그레이션 적용 순서가
    // 어긋나도 기존 동기화가 멀쩡하도록 채널과 실패 처리를 따로 둔다.
    val photoChannel = client.channel("couple-photos:$coupleId")
    photoChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "couple_photos"
        filter("couple_id", FilterOperator.EQ, coupleId)
    }.onEach {
        println("[Realtime] photos change: ${it::class.simpleName}")
        subscription.onPhotosChanged()
    }.catch {
        println("[Realtime] couple_photos 스트림 오류 — 사진 실시간 반영만 중단: $it")
    }.launchIn(this)
    runCatching { photoChannel.subscribe() }
        .onSuccess { println("[Realtime] couple-photos:$coupleId 채널 subscribe 완료") }
        .onFailure { println("[Realtime] couple_photos 구독 실패 (마이그레이션 미적용?): $it") }

    // 부모 Job 이 cancel 되면 여기 도달 · 채널 unsubscribe.
    try { kotlinx.coroutines.awaitCancellation() }
    finally {
        println("[Realtime] couple:$coupleId 채널 unsubscribe")
        client.realtime.removeChannel(channel)
        client.realtime.removeChannel(photoChannel)
    }
}
