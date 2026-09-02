package com.hyunjine.linker.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 프로세스 수명 동안 살아있는 최상위 coroutine scope.
 *
 * `viewModelScope` 로 fire-and-forget write 를 띄우면 VM 이 사라지는 순간
 * (Nav pop · 화면 재구성 등) HTTP in-flight 도 함께 캔슬되어 서버 반영이 유실될 수 있다.
 * "결과는 안 봐도 되지만 반드시 완료돼야 하는" 짧은 서버 write (예: 프리퍼런스 upsert)
 * 는 이 scope 를 써서 VM 라이프사이클과 분리한다.
 *
 * 프로세스가 죽으면 어차피 코루틴도 죽으니 완벽한 보장은 아니지만, 실무상 대부분의 유실
 * 케이스 (VM 재구성 · 화면 전환) 를 막아준다. SupervisorJob 으로 자식 실패가 다른 자식을
 * 전파해 취소시키지 않도록 격리.
 */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)
