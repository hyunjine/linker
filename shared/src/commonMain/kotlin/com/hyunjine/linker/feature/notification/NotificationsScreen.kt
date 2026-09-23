package com.hyunjine.linker.feature.notification

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.NotiAnnouncementBg
import com.hyunjine.linker.designsystem.theme.NotiAnnouncementFg
import com.hyunjine.linker.designsystem.theme.NotiPartnerBg
import com.hyunjine.linker.designsystem.theme.NotiPartnerFg
import com.hyunjine.linker.designsystem.theme.NotiReminderBg
import com.hyunjine.linker.designsystem.theme.NotiReminderFg
import com.hyunjine.linker.designsystem.theme.NotiUpdateBg
import com.hyunjine.linker.designsystem.theme.NotiUpdateFg
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.SkeletonFill
import com.hyunjine.linker.designsystem.theme.SkeletonLabel
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import kotlinx.datetime.TimeZone
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_bell
import linker.shared.generated.resources.ic_bell_empty
import linker.shared.generated.resources.ic_megaphone
import linker.shared.generated.resources.ic_noti_schedule
import linker.shared.generated.resources.ic_sparkles
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val CardShape = RoundedCornerShape(12.dp)

/**
 * 알림 내역 화면 (#303 · Figma Section 4280:86006).
 *
 * 상단바 아래 날짜별 그룹 (라벨 + 흰 카드) 을 쌓고, 맨 아래 보관 기간 안내. 행은 탭 동작 없음.
 * 상태별로 로딩 스켈레톤 · 에러 (다시 시도) · 빈 상태를 보여준다.
 *
 * @param ui 화면 상태.
 * @param onBack 뒤로가기.
 * @param onRetry 에러 상태 "다시 시도".
 * @param onRefresh 리스트 · 빈 상태에서 아래로 당겨 새로고침 (#365).
 */
@Composable
fun NotificationsScreen(
    ui: NotificationsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit = {},
) {
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        AppTopBar(title = "알림", onBack = onBack)
        when {
            ui.loading -> LoadingState()
            ui.error != null -> ErrorState(onRetry = onRetry)
            else -> RefreshableContent(refreshing = ui.refreshing, onRefresh = onRefresh) {
                if (ui.groups.isEmpty()) EmptyState() else NotificationList(groups = ui.groups, bottomInset = bottomInset)
            }
        }
    }
}

/**
 * 당겨서 새로고침 래퍼 (#365). 인디케이터는 흰 원 + 파란 스피너로 카드 톤에 맞춘다.
 * 안쪽 콘텐츠는 스크롤 가능해야 당김이 전달된다 (빈 상태도 LazyColumn 으로 감쌈).
 */
@Composable
private fun RefreshableContent(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = SurfaceCard,
                color = PrimaryBlue,
            )
        },
    ) {
        content()
    }
}

@Composable
private fun NotificationList(groups: List<NotificationGroup>, bottomInset: Dp) {
    val font = LocalPretendardFontFamily.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 20.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(groups, key = { it.label }) { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupLabel(group.label)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(SurfaceCard),
                ) {
                    group.items.forEachIndexed { index, item ->
                        NotificationRow(item = item, timeLabel = group.timeLabels[index])
                    }
                }
            }
        }
        item(key = "retention") {
            Text(
                text = "최근 30일 동안 받은 알림만 보여요",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = font, fontSize = 12.sp, color = TextSecondary),
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        style = TextStyle(
            fontFamily = LocalPretendardFontFamily.current,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = TextSecondary,
        ),
    )
}

/** 알림 한 줄 (Figma Notification Row) — 종류 아이콘 · 제목 + 시간 · 본문 (최대 2줄). */
@Composable
private fun NotificationRow(item: NotificationItem, timeLabel: String) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindIcon(item.kind)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = font,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = TextPrimary,
                    ),
                )
                Text(
                    text = timeLabel,
                    style = TextStyle(fontFamily = font, fontSize = 12.sp, color = TextSecondary),
                )
            }
            Text(
                text = item.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = font, fontSize = 13.sp, color = TextSecondary),
            )
        }
    }
}

/** 종류별 36dp 원 아이콘. */
@Composable
private fun KindIcon(kind: NotificationKind) {
    val (icon, bg, fg) = kind.style()
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(fg),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun NotificationKind.style(): Triple<DrawableResource, Color, Color> = when (this) {
    NotificationKind.Partner -> Triple(Res.drawable.ic_noti_schedule, NotiPartnerBg, NotiPartnerFg)
    NotificationKind.Reminder -> Triple(Res.drawable.ic_bell, NotiReminderBg, NotiReminderFg)
    NotificationKind.Announcement -> Triple(Res.drawable.ic_megaphone, NotiAnnouncementBg, NotiAnnouncementFg)
    NotificationKind.Update -> Triple(Res.drawable.ic_sparkles, NotiUpdateBg, NotiUpdateFg)
}

/** 로딩 스켈레톤 (Figma Screen 3) — 그룹 라벨 자리 + 카드 안 5줄. */
@Composable
private fun LoadingState() {
    val widths = listOf(180 to 120, 140 to 90, 200 to 150, 160 to 110, 120 to 170)
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBar(width = 36.dp, height = 12.dp, color = SkeletonLabel, modifier = Modifier.padding(start = 4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(SurfaceCard),
        ) {
            widths.forEach { (title, body) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SkeletonFill),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBar(width = title.dp, height = 14.dp)
                        SkeletonBar(width = body.dp, height = 12.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp, modifier: Modifier = Modifier, color: Color = SkeletonFill) {
    Box(
        modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color),
    )
}

/** 빈 상태 (Figma Screen 2) — 화면 가운데보다 살짝 위. */
@Composable
private fun EmptyState() {
    val font = LocalPretendardFontFamily.current
    // 빈 상태에서도 당겨서 새로고침이 되도록 스크롤 컨테이너 안에 한 화면 크기로 배치.
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxSize()
                    .offset(y = (-20).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_bell_empty),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(TextPrimary),
                    modifier = Modifier.size(80.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "아직 받은 알림이 없어요",
                    style = TextStyle(
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "일정을 등록하거나\n일정이 곧 시작되면 여기에 모여요",
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontFamily = font,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 1.4.em,
                        color = TextSecondary,
                    ),
                )
            }
        }
    }
}

/** 에러 상태 (Figma Screen 4). */
@Composable
private fun ErrorState(onRetry: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.offset(y = (-20).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "알림을 불러오지 못했어요",
                style = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "네트워크 연결을 확인하고 다시 시도해 주세요",
                style = TextStyle(fontFamily = font, fontSize = 13.sp, color = TextSecondary),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "다시 시도",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PrimaryBlue)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                style = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = SurfaceCard,
                ),
            )
        }
    }
}

// ---------- Preview ----------

@Preview
@Composable
private fun NotificationsScreenPreview() {
    val now = Clock.System.now()
    val items = listOf(
        NotificationItem("1", NotificationKind.Partner, "민교 님이 할 일을 추가했어요", "장보기 · 9월 23일", now - 10.minutes),
        NotificationItem("2", NotificationKind.Reminder, "치과 정기검진", "오후 3:00 시작", now - 1.hours),
        NotificationItem("3", NotificationKind.Update, "v1.5.0 업데이트", "새 버전이 출시되었어요. 업데이트하고 출시 노트를 확인해 보세요!", now - 26.hours),
        NotificationItem("4", NotificationKind.Announcement, "서버 점검 안내", "9월 25일 새벽 2시부터 30분간 접속이 원활하지 않을 수 있어요", now - 72.hours),
    )
    LinkerTheme {
        NotificationsScreen(
            ui = NotificationsUiState(loading = false, groups = groupByDay(items, now, TimeZone.currentSystemDefault())),
            onBack = {},
            onRetry = {},
        )
    }
}
