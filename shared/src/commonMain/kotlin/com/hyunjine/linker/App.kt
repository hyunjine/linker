package com.hyunjine.linker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.hyunjine.linker.auth.KakaoLoginResult
import com.hyunjine.linker.auth.rememberKakaoLoginClient
import com.hyunjine.linker.ui.couple.CoupleLinkScreen
import com.hyunjine.linker.ui.login.LoginScreen
import com.hyunjine.linker.ui.main.MainScreen
import com.hyunjine.linker.ui.profile.ProfileSetupScreen
import com.hyunjine.linker.ui.schedule.CreateScheduleScreen
import com.hyunjine.linker.ui.theme.ProvidePretendard
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
            // 온보딩 완료(커플 연결) 시점에 로그인·프로필·연결 스택을 전부 비우고 Main 만 남긴다.
            // 홈에서 뒤로가기로 로그인 화면이 다시 뜨면 안 되므로 clear + push 조합.
            val goHome: () -> Unit = {
                backStack.clear()
                backStack.add(MainRoute)
            }
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<LoginRoute> {
                        val kakao = rememberKakaoLoginClient()
                        val scope = rememberCoroutineScope()
                        LoginScreen(
                            onKakaoLoginClick = {
                                scope.launch {
                                    // TODO(#17 후속): 성공 시 서버 POST /auth/kakao 로 토큰 교환 →
                                    // is_profile_complete / couple 응답에 따라 라우팅 (§3.4 매트릭스).
                                    // 지금은 SDK 성공만 되면 프로필 셋업으로 진입.
                                    when (val r = kakao.login()) {
                                        is KakaoLoginResult.Success -> backStack.add(ProfileSetupRoute)
                                        KakaoLoginResult.Cancelled -> Unit
                                        is KakaoLoginResult.Failure -> {
                                            // TODO: 사용자에게 토스트/스낵바. 지금은 조용히 무시.
                                            println("[Kakao] login failed: ${r.reason}")
                                        }
                                    }
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
