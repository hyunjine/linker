package com.hyunjine.linker.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyunjine.linker.ui.theme.SurfaceCard

/**
 * 앱 전역에서 사용하는 좌측 드로워. Material3 [ModalNavigationDrawer] 를 감싼 얇은 래퍼.
 * 스크림/슬라이드 애니메이션/좌→우 스와이프 dismiss 는 material 기본 동작에 위임.
 *
 * @param drawerState 열림/닫힘 상태. 상위에서 [rememberDrawerState] 로 만들어 소유.
 * @param drawerContent 드로워 시트 안에 그릴 콘텐츠. [ColumnScope] 로 제공됨.
 * @param modifier 최상위 [ModalNavigationDrawer] 에 적용할 [Modifier].
 * @param content 드로워가 위에 겹쳐질 메인 콘텐츠 (드로워 닫혔을 때 보이는 화면).
 */
@Composable
fun AppDrawer(
    drawerState: DrawerState,
    drawerContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceCard,
                // 우측 상단·하단만 라운드 (드로워는 좌측에서 나오므로 좌측 모서리는 화면 밖).
                drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                content = drawerContent,
            )
        },
        modifier = modifier,
        content = content,
    )
}
