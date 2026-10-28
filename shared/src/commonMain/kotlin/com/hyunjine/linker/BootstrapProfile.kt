package com.hyunjine.linker

import com.hyunjine.linker.data.remote.UsersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * 부트스트랩 (스플래시 → 첫 화면) 시점에 확인한 내 프로필 상태 (#424).
 * "조회 실패" 와 "정말 미완성" 을 구분하기 위해 [Unknown] 을 따로 둔다.
 */
internal enum class BootstrapProfileState {
    /** `profile_completed_at` 이 채워진 프로필 확인. */
    Completed,

    /** row 는 조회됐지만 미완성이거나, 조회 성공인데 row 자체가 없음 — 온보딩 필요가 확정된 상태. */
    Incomplete,

    /** 네트워크 · 서버 오류 · 타임아웃으로 확인 못함. 미완성으로 취급하지 않는다. */
    Unknown,
}

/** 부트스트랩이 고를 수 있는 첫 화면. App.kt 의 private NavKey 와 1:1 매핑. */
internal enum class BootstrapDestination { Main, ProfileSetup }

/**
 * `myProfile()` 1회 호출 결과 → 프로필 상태.
 *
 * @param result `UsersRepository.myProfile()` 호출 결과. 실패 (예외) 는 [BootstrapProfileState.Unknown],
 *  성공 + null (row 없음) 은 [BootstrapProfileState.Incomplete] 로 본다.
 */
internal fun bootstrapProfileStateOf(result: Result<UsersRepository.Profile?>): BootstrapProfileState =
    result.fold(
        onSuccess = { profile ->
            if (profile?.isCompleted == true) BootstrapProfileState.Completed else BootstrapProfileState.Incomplete
        },
        onFailure = { BootstrapProfileState.Unknown },
    )

/**
 * 프로필 상태 → 첫 화면. 프로필 설정 화면은 미완성이 **확정** 된 경우에만 보낸다.
 *
 * 재시도 후에도 [BootstrapProfileState.Unknown] 이면 Main 으로 보낸다. 이미 가입한 유저가 대부분이고,
 * Main 은 자체적으로 데이터를 (캐시 포함) 로드 · 재시도하므로 오프라인에서도 쓸 수 있다.
 * 반대로 스플래시에서 무한 대기하면 네트워크가 없을 때 앱을 아예 못 쓰게 된다.
 * 실제 미완성 유저가 이 경로로 Main 에 가더라도 다음 앱 실행 때 다시 판정된다.
 *
 * @param state [fetchBootstrapProfileState] 로 확인한 프로필 상태.
 */
internal fun bootstrapDestinationOf(state: BootstrapProfileState): BootstrapDestination = when (state) {
    BootstrapProfileState.Incomplete -> BootstrapDestination.ProfileSetup
    BootstrapProfileState.Completed,
    BootstrapProfileState.Unknown -> BootstrapDestination.Main
}

/** 시도 간 대기 (ms). 크기 + 1 = 총 시도 횟수 (3회). */
private val PROFILE_RETRY_BACKOFF_MS = listOf(500L, 1_000L)

/** 시도 1회 타임아웃 (ms). 최악의 경우 3 × 2s + 1.5s ≈ 7.5s 안에 판정이 끝난다. */
private const val PROFILE_ATTEMPT_TIMEOUT_MS = 2_000L

/**
 * 내 프로필 상태를 재시도하며 확정한다. 확정 ([BootstrapProfileState.Completed] ·
 * [BootstrapProfileState.Incomplete]) 되는 즉시 반환하고, 모든 시도가 실패하면
 * [BootstrapProfileState.Unknown].
 *
 * 세션 복원 직후 첫 요청은 토큰 갱신 · 네트워크 준비 타이밍과 겹쳐 일시적으로 실패하는 일이 있어
 * 1회 실패만으로 판단하지 않는다. 호출 코루틴이 취소되면 그대로 취소를 전파한다.
 */
internal suspend fun fetchBootstrapProfileState(): BootstrapProfileState {
    val attempts = PROFILE_RETRY_BACKOFF_MS.size + 1
    repeat(attempts) { attempt ->
        if (attempt > 0) delay(PROFILE_RETRY_BACKOFF_MS[attempt - 1])
        val result: Result<UsersRepository.Profile?> = try {
            Result.success(withTimeout(PROFILE_ATTEMPT_TIMEOUT_MS) { UsersRepository.myProfile() })
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        val state = bootstrapProfileStateOf(result)
        if (state != BootstrapProfileState.Unknown) return state
        println("[Boot] myProfile 실패 (${attempt + 1}/$attempts): ${result.exceptionOrNull()}")
    }
    return BootstrapProfileState.Unknown
}
