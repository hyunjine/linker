package com.hyunjine.linker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.hyunjine.linker.auth.sessionStatus
import com.hyunjine.linker.auth.signInWithKakao
import com.hyunjine.linker.ui.couple.CoupleLinkScreen
import com.hyunjine.linker.ui.login.LoginScreen
import com.hyunjine.linker.ui.main.MainScreen
import com.hyunjine.linker.ui.profile.ProfileSetupScreen
import com.hyunjine.linker.ui.schedule.CreateScheduleScreen
import com.hyunjine.linker.ui.theme.ProvidePretendard
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * 앱 최상위 네비게이션 그래프. Navigation3 [NavDisplay] 로 백스택을 직접 소유한다.
 * 각 목적지 [NavKey] 는 `@Serializable` 이고, [NavConfig] 의 polymorphic 서브클래스로 등록해야
 * saved state 복원이 가능하다 (KMP 는 리플렉션이 없어 명시 등록 필수).
 */
@Serializable
private data object LoginRoute : NavKey

@Serializable
private data object MainRoute : NavKey

@Serializable
private data object ProfileSetupRoute : NavKey

@Serializable
private data object CoupleLinkRoute : NavKey

@Serializable
private data object CreateScheduleRoute : NavKey

private val NavConfig: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(LoginRoute::class, LoginRoute.serializer())
            subclass(MainRoute::class, MainRoute.serializer())
            subclass(ProfileSetupRoute::class, ProfileSetupRoute.serializer())
            subclass(CoupleLinkRoute::class, CoupleLinkRoute.serializer())
            subclass(CreateScheduleRoute::class, CreateScheduleRoute.serializer())
        }
    }
}

@Composable
fun App() {
    MaterialTheme {
        ProvidePretendard {
            val backStack = rememberNavBackStack(NavConfig, LoginRoute)
            val scope = rememberCoroutineScope()
            // 온보딩 완료(커플 연결) 시점에 로그인·프로필·연결 스택을 전부 비우고 Main 만 남긴다.
            // 홈에서 뒤로가기로 로그인 화면이 다시 뜨면 안 되므로 clear + push 조합.
            val goHome: () -> Unit = {
                backStack.clear()
                backStack.add(MainRoute)
            }

            // Supabase Auth 세션 상태가 Authenticated 로 바뀌면 다음 온보딩 단계로 진입.
            // 실제 프로필/커플 상태 조회 (#40 후속) 붙기 전엔 무조건 프로필 셋업으로 라우팅.
            val status by sessionStatus.collectAsState()
            LaunchedEffect(status) {
                println("[Auth] sessionStatus = ${status::class.simpleName}")
                if (status is SessionStatus.Authenticated && backStack.lastOrNull() == LoginRoute) {
                    println("[Auth] Authenticated 감지 → ProfileSetup 으로 이동")
                    backStack.add(ProfileSetupRoute)
                }
            }

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<LoginRoute> {
                        LoginScreen(
                            onKakaoLoginClick = {
                                println("[Auth] 카카오 버튼 click")
                                scope.launch {
                                    runCatching { signInWithKakao() }
                                        .onFailure { println("[Auth] signInWithKakao 실패: $it") }
                                }
                            },
                        )
                    }
                    entry<ProfileSetupRoute> {
                        ProfileSetupScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onNext = { backStack.add(CoupleLinkRoute) },
                        )
                    }
                    entry<CoupleLinkRoute> {
                        CoupleLinkScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onLink = { _ -> goHome() },
                        )
                    }
                    entry<MainRoute> {
                        MainScreen(
                            onAddSchedule = { backStack.add(CreateScheduleRoute) },
                        )
                    }
                    entry<CreateScheduleRoute> {
                        CreateScheduleScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onSave = { /* TODO(#15 후속): repository 저장 */ backStack.removeLastOrNull() },
                        )
                    }
                },
            )
        }
    }
}
