package com.hyunjine.linker.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hyunjine.linker.data.Secrets
import com.hyunjine.linker.designsystem.theme.AvatarPlaceholderBg
import com.hyunjine.linker.designsystem.theme.AvatarPlaceholderFg
import com.hyunjine.linker.designsystem.theme.DrawerBottomNavBorder
import com.hyunjine.linker.designsystem.theme.DrawerAddPartnerDash
import com.hyunjine.linker.designsystem.theme.DrawerButtonBg
import com.hyunjine.linker.designsystem.theme.DrawerCheckBlue
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_dday
import linker.shared.generated.resources.ic_heart
import linker.shared.generated.resources.ic_link_alt
import linker.shared.generated.resources.ic_plus
import linker.shared.generated.resources.ic_setting_two
import linker.shared.generated.resources.ic_check
import linker.shared.generated.resources.ic_school
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** 사이드 드로워의 캘린더 표시 옵션 상태. */
data class DrawerDisplayState(
    val showMyCalendar: Boolean = true,
    val showPartnerCalendar: Boolean = true,
    /** 공동 (Us) 일정 · 할일 표시. 내 · 상대방과 독립적으로 켤/끌 수 있음. */
    val showSharedCalendar: Boolean = true,
    val showHolidays: Boolean = true,
    val showSolarTerms: Boolean = true,
)

/**
 * 메인 화면 사이드 드로워 콘텐츠. Figma 3114:76134 참고.
 *
 * 구성:
 *  - 커플 헤더 (#335) — 겹친 아바타 (나 · 상대방) + 이름 + "내 프로필" · "상대방 연결"/"커플 설정" 버튼
 *  - "기념일 설정" 진입 row
 *  - "일정 표시" 섹션 — 내 캘린더 / 상대방 캘린더
 *  - "달력 정보 표시" 섹션 — 공휴일 / 절기
 *
 * 이 컴포저블은 [AppDrawer] 의 `drawerContent` 슬롯에서 호출됨.
 */
@Composable
fun MainDrawerContent(
    profileName: String,
    displayState: DrawerDisplayState,
    profileImageUrl: String? = null,
    /** 파트너 닉네임 · 사진. [hasPartner] 가 true 일 때만 헤더에 노출. */
    partnerName: String = "",
    partnerImageUrl: String? = null,
    onSettingsClick: () -> Unit = {},
    onAnniversaryClick: () -> Unit = {},
    onCoupleLinkClick: () -> Unit = {},
    onTasksClick: () -> Unit = {},
    onReleaseNotesClick: () -> Unit = {},
    onToggleMyCalendar: (Boolean) -> Unit = {},
    onTogglePartnerCalendar: (Boolean) -> Unit = {},
    onToggleSharedCalendar: (Boolean) -> Unit = {},
    onToggleHolidays: (Boolean) -> Unit = {},
    onToggleSolarTerms: (Boolean) -> Unit = {},
    onLogout: () -> Unit = {},
    onEverytimeTimetableClick: () -> Unit = {},
    /** 파트너 조인 여부. false 면 "상대방 캘린더" · "공동 캘린더" 토글 자체를 감춘다. */
    hasPartner: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(SurfaceCard),
    ) {
        // 상단 · 중단 콘텐츠는 스크롤 가능한 weight 영역에 배치. 하단 액션바 (기념일 · 에브리타임)
        // 는 항상 드로워 바닥에 고정 — 옵션이 많아져도 하단 진입점이 스크롤로 밀리지 않음 (#327).
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            CoupleProfileHeader(
                myName = profileName,
                myImageUrl = profileImageUrl,
                partnerName = partnerName.takeIf { hasPartner },
                partnerImageUrl = partnerImageUrl,
                onMyProfileClick = onSettingsClick,
                onCoupleClick = onCoupleLinkClick,
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel(text = "일정 표시")
            ToggleRow(
                text = "내 캘린더",
                checked = displayState.showMyCalendar,
                onCheckedChange = onToggleMyCalendar,
            )
            if (hasPartner) {
                // 상대방 · 공동 개념은 파트너가 있을 때만 의미. Solo 상태에선 감춰서 사용자 혼란 방지.
                ToggleRow(
                    text = "상대방 캘린더",
                    checked = displayState.showPartnerCalendar,
                    onCheckedChange = onTogglePartnerCalendar,
                )
                ToggleRow(
                    text = "공동 캘린더",
                    checked = displayState.showSharedCalendar,
                    onCheckedChange = onToggleSharedCalendar,
                )
            }
            SectionLabel(text = "달력 정보 표시")
            ToggleRow(
                text = "공휴일",
                checked = displayState.showHolidays,
                onCheckedChange = onToggleHolidays,
            )
            ToggleRow(
                text = "절기",
                checked = displayState.showSolarTerms,
                onCheckedChange = onToggleSolarTerms,
            )
            Spacer(Modifier.height(16.dp))
            TasksRow(onClick = onTasksClick)
            ReleaseNotesRow(onClick = onReleaseNotesClick)
            AppVersionRow()
            LogoutRow(onClick = onLogout)
        }
        // 하단 고정 액션바 — 기념일 (#182) · 에브리타임 (#306). 파트너 · URL 등록 여부와 무관하게
        // 항상 두 탭 노출. 에브리타임 진입 후 empty 상태 처리는 EverytimeTimetableScreen 담당.
        HorizontalDivider(
            thickness = 1.dp,
            color = DrawerBottomNavBorder
        )
        DrawerBottomNav(
            onAnniversaryClick = onAnniversaryClick,
            onEverytimeClick = onEverytimeTimetableClick,
        )
    }
}

/**
 * 드로워 최하단 고정 액션바 (#327 · #329). 좌측 "디데이" · 우측 "에브리타임" 두 탭 균등 배치.
 * 각 탭은 24dp 아이콘 위, 12sp SemiBold 라벨 아래 형태 — Figma 4168:78837 참고.
 */
@Composable
private fun DrawerBottomNav(
    onAnniversaryClick: () -> Unit,
    onEverytimeClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard)
            .padding(vertical = 12.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrawerBottomNavItem(
            label = "디데이",
            onClick = onAnniversaryClick,
            modifier = Modifier.weight(1f),
        ) {
            // 캘린더 + 하트 (#385 · Figma 4401:79774).
            Image(
                painter = painterResource(Res.drawable.ic_dday),
                contentDescription = null,
                colorFilter = ColorFilter.tint(TextPrimary),
                modifier = Modifier.size(24.dp),
            )
        }
        DrawerBottomNavItem(
            label = "에브리타임",
            onClick = onEverytimeClick,
            modifier = Modifier.weight(1f),
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_school),
                contentDescription = null,
                colorFilter = ColorFilter.tint(TextPrimary),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 하단 액션바의 한 탭 (아이콘 slot + 라벨). */
@Composable
private fun DrawerBottomNavItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val pretendard = LocalPretendardFontFamily.current
    Column(
        modifier = modifier
            .noRippleClickable(onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        icon()
        Text(
            text = label,
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = TextPrimary,
            ),
        )
    }
}

/**
 * 드로워 하단 "앱 버전" 행 (#385). 좌측 라벨 · 우측 `vX.Y.Z`. 탭 동작이 없어 리플 없이, 다른 텍스트 행과
 * 글자 위치 (좌우 20dp) · 크기를 맞춘다. 값은 빌드 때 `Config.xcconfig` 의 MARKETING_VERSION 에서 생성.
 */
@Composable
private fun AppVersionRow() {
    val style = TextStyle(
        fontFamily = LocalPretendardFontFamily.current,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = TextPrimary,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "앱 버전", style = style, modifier = Modifier.weight(1f))
        Text(text = "v${Secrets.AppVersion}", style = style)
    }
}

/** 드로워 하단 텍스트 행 (할 일 · 릴리즈 노트 · 로그아웃) 리플 모양. */
private val DrawerRowRippleShape = RoundedCornerShape(10.dp)

/** 드로워 하단 "할 일" 진입 행 (#304). [ReleaseNotesRow] 와 동일한 스타일 · 리플 피드백. */
@Composable
private fun TasksRow(onClick: () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 리플 끝을 살짝 둥글게 — 좌우 8dp 안쪽으로 들여 모서리가 드로워 가장자리에 붙지 않게.
            // 텍스트 시작 위치는 8 + 12 = 20dp 로 기존과 동일.
            .padding(horizontal = 8.dp)
            .clip(DrawerRowRippleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "할 일",
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextPrimary,
            ),
        )
    }
}

/**
 * 드로워 하단 "릴리즈 노트" 진입 행. [LogoutRow] 와 동일한 텍스트 스타일이나 컬러만 다르게 —
 * [TextPrimary] 로 로그아웃 대비 강조 (#255).
 */
@Composable
private fun ReleaseNotesRow(onClick: () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 리플 끝을 살짝 둥글게 — 좌우 8dp 안쪽으로 들여 모서리가 드로워 가장자리에 붙지 않게.
            // 텍스트 시작 위치는 8 + 12 = 20dp 로 기존과 동일.
            .padding(horizontal = 8.dp)
            .clip(DrawerRowRippleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "릴리즈 노트",
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextPrimary,
            ),
        )
    }
}

/** 드로워 하단 로그아웃 버튼. 강조 색 없이 텍스트만 (좌측 정렬). */
@Composable
private fun LogoutRow(onClick: () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 리플 끝을 살짝 둥글게 — 좌우 8dp 안쪽으로 들여 모서리가 드로워 가장자리에 붙지 않게.
            // 텍스트 시작 위치는 8 + 12 = 20dp 로 기존과 동일.
            .padding(horizontal = 8.dp)
            .clip(DrawerRowRippleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "로그아웃",
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextSecondary,
            ),
        )
    }
}

/** 커플 헤더 아바타 지름 (#335 · Figma 72). */
private val CoupleAvatarSize = 72.dp

/** 아바타 사이를 가르는 흰 테두리 두께 — 겹친 부분 경계. */
private val CoupleAvatarRing = 3.dp

/** 두 아바타가 겹치는 폭. */
private val CoupleAvatarOverlap = 17.dp

/**
 * 드로워 상단 커플 헤더 (#335 · Figma 4329:79092). 연결 전후 같은 틀을 유지해 화면이 흔들리지 않게 한다.
 *
 *  - 연결 전: 내 아바타 + 점선 원 `+` · 내 이름 · 안내 문구 · [내 프로필] [상대방 연결]
 *  - 연결 후: 내 아바타 + 상대방 아바타 · `나 ♥ 상대` · [내 프로필] [커플 설정]
 *  - 겹침 순서는 항상 오른쪽 (상대방 · `+`) 이 위.
 *
 * @param myName 내 닉네임.
 * @param myImageUrl 내 프로필 사진. null 이면 이름 첫 글자.
 * @param partnerName 파트너 닉네임. null 이면 미연결 상태로 그린다.
 * @param partnerImageUrl 파트너 프로필 사진.
 * @param onMyProfileClick "내 프로필" → 프로필 편집.
 * @param onCoupleClick "상대방 연결" · "커플 설정" → 커플 연결/관리 화면.
 */
@Composable
private fun CoupleProfileHeader(
    myName: String,
    myImageUrl: String?,
    partnerName: String?,
    partnerImageUrl: String?,
    onMyProfileClick: () -> Unit,
    onCoupleClick: () -> Unit,
) {
    val pretendard = LocalPretendardFontFamily.current
    val nameStyle = TextStyle(
        fontFamily = pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = TextPrimary,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 테두리가 바깥으로 3dp 나가는 Figma 와 맞추려고 각 칸을 링 두께만큼 키우고, 그만큼 더 겹친다.
        Row(horizontalArrangement = Arrangement.spacedBy(-(CoupleAvatarOverlap + CoupleAvatarRing * 2))) {
            CoupleAvatar(name = myName, imageUrl = myImageUrl, description = "내 프로필 사진")
            if (partnerName != null) {
                CoupleAvatar(name = partnerName, imageUrl = partnerImageUrl, description = "상대방 프로필 사진")
            } else {
                AddPartnerAvatar()
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = myName, style = nameStyle)
            if (partnerName != null) {
                Image(
                    painter = painterResource(Res.drawable.ic_heart),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(text = partnerName, style = nameStyle)
            }
        }
        if (partnerName == null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "상대방과 연결하고 일정을 공유해요",
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = TextSecondary,
                ),
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(20.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderActionButton(
                icon = Res.drawable.ic_setting_two,
                label = "내 프로필",
                onClick = onMyProfileClick,
                modifier = Modifier.weight(1f),
            )
            HeaderActionButton(
                icon = Res.drawable.ic_link_alt,
                label = if (partnerName != null) "커플 설정" else "상대방 연결",
                onClick = onCoupleClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 흰 테두리를 두른 원형 아바타. 사진이 없으면 이름 첫 글자. */
@Composable
private fun CoupleAvatar(name: String, imageUrl: String?, description: String) {
    Box(
        modifier = Modifier
            .size(CoupleAvatarSize + CoupleAvatarRing * 2)
            .clip(CircleShape)
            .background(SurfaceCard)
            .padding(CoupleAvatarRing)
            .clip(CircleShape)
            .background(AvatarPlaceholderBg),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(CoupleAvatarSize).clip(CircleShape),
            )
        } else {
            Text(
                text = name.take(1),
                style = TextStyle(
                    fontFamily = LocalPretendardFontFamily.current,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 26.sp,
                    color = AvatarPlaceholderFg,
                ),
            )
        }
    }
}

/** 미연결 상태의 상대방 자리 — 회색 원 + 점선 테두리 + 가운데 `+`. */
@Composable
private fun AddPartnerAvatar() {
    Box(
        modifier = Modifier.size(CoupleAvatarSize + CoupleAvatarRing * 2),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(CoupleAvatarSize)
                .clip(CircleShape)
                .background(DrawerButtonBg)
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawCircle(
                        color = DrawerAddPartnerDash,
                        radius = (size.minDimension - stroke) / 2,
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_plus),
                contentDescription = "상대방 연결",
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** 헤더 하단 반반 버튼 — 회색 라운드 + 16dp 아이콘 + SemiBold 14. */
@Composable
private fun HeaderActionButton(
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DrawerButtonBg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(TextPrimary),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = TextStyle(
                fontFamily = LocalPretendardFontFamily.current,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextPrimary,
            ),
        )
    }
}


@Composable
private fun SectionLabel(text: String) {
    val pretendard = LocalPretendardFontFamily.current
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        style = TextStyle(
            fontFamily = pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = TextSecondary,
        ),
    )
}

/**
 * Figma 드로워 표시 옵션 row. 좌측 22dp 사각 체크박스 + 15sp 라벨. Row 전체가 탭 타겟 (토글).
 * 체크 상태 = 파란 fill + 흰 체크마크, 언체크 = 파란 테두리만.
 * (Figma 에는 우측 chevron 도 있지만 세부 필터 화면이 아직 없어 이번 스코프에서 제외.)
 */
@Composable
private fun ToggleRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckboxSquare(checked = checked)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextPrimary,
            ),
        )
    }
}

/** Figma 체크박스: 22dp · rounded 5 · 체크 시 fill, 언체크 시 2dp 파란 테두리. */
@Composable
private fun CheckboxSquare(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .then(
                if (checked) Modifier.background(DrawerCheckBlue)
                else Modifier.border(2.dp, DrawerCheckBlue, RoundedCornerShape(5.dp)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Image(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 리플 없는 clickable. 드로워/시트처럼 자체 시각 피드백 (체크박스 색 변화 등) 이 있는 곳용. */
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}


// 프리뷰 폭 · 높이는 실제 드로워 폭 (315dp) · iPhone 15 세로 (874dp) 근사. 하단 액션바가 바닥에
// 고정되는 걸 눈으로 확인하려면 heightDp 를 반드시 명시 (Column.fillMaxHeight() 만으로는 프리뷰
// 캔버스가 wrap-content 로 잡혀 액션바가 콘텐츠 바로 아래에 붙어버림).
@Composable
@Preview(showBackground = true, widthDp = 315, heightDp = 874)
private fun MainDrawerContentSoloPreview() {
    LinkerTheme {
        MainDrawerContent(
            profileName = "김현진",
            displayState = DrawerDisplayState(),
            hasPartner = false,
        )
    }
}

@Composable
@Preview(showBackground = true, widthDp = 315, heightDp = 874)
private fun MainDrawerContentCouplePreview() {
    LinkerTheme {
        MainDrawerContent(
            profileName = "김현진",
            partnerName = "밍교",
            displayState = DrawerDisplayState(
                showMyCalendar = true,
                showPartnerCalendar = true,
                showSharedCalendar = false,
                showHolidays = true,
                showSolarTerms = false,
            ),
            hasPartner = true,
        )
    }
}

