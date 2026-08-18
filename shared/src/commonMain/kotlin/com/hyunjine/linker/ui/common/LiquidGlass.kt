package com.hyunjine.linker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * iOS 26 리퀴드 글래스 프리셋. 앱 전역에서 같은 톤을 유지하기 위해 값은 한 곳에서만 정의한다.
 * 개별 파라미터를 [Modifier.liquidGlass] 에서 오버라이드해 세부 조정 가능.
 */
object LiquidGlassDefaults {
    /** 유리 위에 얹는 프로스트 서피스 색. iOS 라이트 모달 톤에 맞춘 흰색 반투명. */
    val Tint: Color = Color.White.copy(alpha = 0.18f)
    /** 기본 블러 반경. 배경이 살짝만 뿌옇게 보일 정도의 crisp 유리감. */
    val BlurRadius = 3.dp
    /** 렌즈 굴절이 만들어지는 가장자리 폭. */
    val LensRefractionHeight = 10.dp
    /** 굴절 세기. 크면 가장자리에서 배경이 크게 왜곡. */
    val LensRefractionAmount = 20.dp
    /** 눌렸을 때 추가로 얹히는 렌즈 굴절량 (pressProgress 1f 기준). */
    val LensPressBoostHeight = 4.dp
    /** 눌렸을 때 추가로 얹히는 굴절 세기. */
    val LensPressBoostAmount = 6.dp
    /** 소프트 드롭 섀도우 반경. */
    val ShadowRadius = 8.dp
    /** 섀도우 색. 진하지 않게 살짝만. */
    val ShadowColor: Color = Color.Black.copy(alpha = 0.12f)
}

/**
 * iOS 26 리퀴드 글래스 스타일을 임의 요소에 얹는 Modifier. 내부적으로 `backdrop` 라이브러리의
 * `drawBackdrop` 을 프리셋 값 (vibrancy · blur · lens · Ambient highlight · soft shadow · frost tint)
 * 으로 감싼 것.
 *
 * 사용하려면 화면 스캐폴드에서 `rememberLayerBackdrop()` 로 만든 [Backdrop] 을 벽지/스크롤
 * 콘텐츠에 `Modifier.layerBackdrop(backdrop)` 로 연결하고, 같은 인스턴스를 넘긴다.
 *
 * 사이즈/`clickable` 등은 이 Modifier 뒤에 체이닝. 아래 순서가 안전:
 *   `Modifier.liquidGlass(backdrop).size(44.dp).clickable { ... }`
 *
 * @param backdrop 유리가 샘플링할 배경 레이어.
 * @param shape 유리 서피스 형태. 기본 [CircleShape].
 * @param tint 유리 위에 얹는 프로스트 색. `alpha == 0` 이면 서피스 fill 생략.
 * @param pressProgress 눌림 진행도 (0..1) 를 반환하는 람다. 지정 시 눌린 순간 렌즈 굴절 강화
 *        + chromatic aberration 이 살짝 켜진다. 기본값은 항상 0 (정적 유리).
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape = CircleShape,
    tint: Color = LiquidGlassDefaults.Tint,
    pressProgress: () -> Float = { 0f },
): Modifier {
    // Layoutlib(Compose Preview 렌더러) 는 drawBackdrop 의 shader 캡처 사이클을 처리하지 못해
    // prepareTreeImpl 재귀로 스택 오버플로우 크래시가 난다. 프리뷰에서는 유리 대신 단색 tint 로
    // 폴백해서 스택은 유지하되 형태만 확인할 수 있게 한다.
    if (LocalInspectionMode.current) {
        return this.background(color = tint, shape = shape)
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(LiquidGlassDefaults.BlurRadius.toPx())
            // pressProgress 를 lambda 호출로 읽어 draw 시점마다 최신 값 반영. 애니메이션 중에도
            // Modifier 를 재생성하지 않고 shader 파라미터만 갱신된다.
            val press = pressProgress()
            lens(
                refractionHeight = LiquidGlassDefaults.LensRefractionHeight.toPx() +
                    LiquidGlassDefaults.LensPressBoostHeight.toPx() * press,
                refractionAmount = LiquidGlassDefaults.LensRefractionAmount.toPx() +
                    LiquidGlassDefaults.LensPressBoostAmount.toPx() * press,
                chromaticAberration = press > 0f,
            )
        },
        highlight = { Highlight.Ambient },
        shadow = { Shadow(radius = LiquidGlassDefaults.ShadowRadius, color = LiquidGlassDefaults.ShadowColor) },
        onDrawSurface = {
            if (tint.alpha > 0f) drawRect(tint)
        },
    )
}
