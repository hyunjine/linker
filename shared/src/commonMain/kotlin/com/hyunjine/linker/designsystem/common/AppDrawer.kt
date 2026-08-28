package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary

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

// ---------- Previews ----------
// ModalNavigationDrawer 는 실제 화면에서만 슬라이드/스크림이 동작하므로 프리뷰에서는 열린 상태의
// 시트 컨테이너 모양만 카드로 근사해 콘텐츠 톤을 확인.

@Preview
@Composable
private fun AppDrawerPreview_OpenState() {
    ProvidePretendard {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceGray),
        ) {
            // 드로워 시트 (좌측 정렬, 우측만 라운드) — ModalDrawerSheet 시각 근사
            Column(
                modifier = Modifier
                    .width(315.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                    .background(SurfaceCard)
                    .padding(20.dp),
            ) {
                Text(
                    text = "샘플 드로워",
                    style = TextStyle(
                        fontFamily = LocalPretendardFontFamily.current,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = TextPrimary,
                    ),
                )
                Spacer(Modifier.height(16.dp))
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "메뉴 항목 ${it + 1}",
                            style = TextStyle(
                                fontFamily = LocalPretendardFontFamily.current,
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp,
                                color = TextPrimary,
                            ),
                        )
                    }
                }
            }
        }
    }
}
