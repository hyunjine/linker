package com.hyunjine.linker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.hyunjine.linker.ui.couple.CoupleLinkScreen
import com.hyunjine.linker.ui.profile.ProfileSetupScreen
import com.hyunjine.linker.ui.theme.ProvidePretendard
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
private data object ProfileSetupRoute : NavKey

@Serializable
private data object CoupleLinkRoute : NavKey

private val NavConfig: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(ProfileSetupRoute::class, ProfileSetupRoute.serializer())
            subclass(CoupleLinkRoute::class, CoupleLinkRoute.serializer())
        }
    }
}

@Composable
fun App() {
    MaterialTheme {
        ProvidePretendard {
            val backStack = rememberNavBackStack(NavConfig, ProfileSetupRoute)
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<ProfileSetupRoute> {
                        ProfileSetupScreen(
                            onNext = { backStack.add(CoupleLinkRoute) },
                        )
                    }
                    entry<CoupleLinkRoute> {
                        CoupleLinkScreen(
                            onCancel = { backStack.removeLastOrNull() },
                        )
                    }
                },
            )
        }
    }
}
