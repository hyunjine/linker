package com.hyunjine.linker.feature.release

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.theme.Chevron
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_chevron_down
import org.jetbrains.compose.resources.painterResource
import com.hyunjine.linker.data.remote.ReleasesRepository
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary

/**
 * 릴리즈 노트 화면. 드로워 진입 시 GitHub Releases API 를 호출해 첫 버전부터 시간순으로 표시.
 *
 * 상태별:
 *  - `loading` — 상단바 아래 중앙 스피너
 *  - `error != null` — 에러 메시지 + 재시도 텍스트
 *  - `releases` 정상 — [ReleaseCard] 리스트
 */
@Composable
fun ReleaseNotesScreen(
    releases: List<ReleasesRepository.GithubRelease>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
) {
    // 하단 safe area 는 outer Column 에서 소비하지 않고 스크롤 컨테이너의 contentPadding 으로 넘겨
    // 콘텐츠가 홈 인디케이터 뒤까지 스크롤 되도록. 정지 시 마지막 아이템은 인디케이터 위에 안착.
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            AppTopBar(title = "릴리즈 노트", onBack = onBack)
            when {
                loading -> LoadingState()
                error != null -> ErrorState(message = error, onRetry = onRetry)
                releases.isEmpty() -> EmptyState()
                else -> ReleaseList(releases, bottomInset = bottomInset)
            }
        }
    }
}

@Composable
private fun ReleaseList(
    releases: List<ReleasesRepository.GithubRelease>,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 12.dp + bottomInset,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(releases, key = { _, r -> r.tagName }) { index, release ->
            // 최신 (0번) 만 기본 펼침 — 사용자가 앱 켜자마자 최신 변경사항 즉시 확인.
            // 이전 버전들은 접힌 상태로 노출해 리스트가 폭발적으로 길어지지 않게.
            ReleaseCard(release = release, defaultExpanded = index == 0)
        }
    }
}

/**
 * 단일 릴리즈 카드 — 상단 헤더 (제목 + 날짜 + chevron) 는 항상 노출, 하단 body 는 접기/펼치기.
 * chevron 은 0° (접힘) ↔ 180° (펼침) 회전 애니메이션.
 */
@Composable
private fun ReleaseCard(
    release: ReleasesRepository.GithubRelease,
    defaultExpanded: Boolean,
) {
    val font = LocalPretendardFontFamily.current
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = !expanded },
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
                    style = TextStyle(
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                    ),
                )
                release.publishedAt?.take(10)?.let { date ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = date,
                        style = TextStyle(
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = font,
                        ),
                    )
                }
            }
            Image(
                painter = painterResource(Res.drawable.ic_chevron_down),
                contentDescription = if (expanded) "접기" else "펼치기",
                colorFilter = ColorFilter.tint(Chevron),
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                MarkdownReleaseBody(release.body.orEmpty())
            }
        }
    }
}

/**
 * GitHub 릴리즈 body 마크다운 간이 렌더러. 우리 릴리즈 노트는 관습적으로:
 *  - `## 섹션` — 섹션 제목 (예: "신규 기능", "개선", "버그 수정")
 *  - `- 항목` — 불릿
 *  - 그 외 — 일반 텍스트 라인
 * 만 사용하므로 세 케이스만 지원. 서드파티 마크다운 라이브러리 없이 100줄 미만으로 자립.
 */
@Composable
private fun MarkdownReleaseBody(body: String) {
    val font = LocalPretendardFontFamily.current
    if (body.isBlank()) {
        Text(
            text = "설명이 없습니다.",
            style = TextStyle(color = TextSecondary, fontSize = 13.sp, fontFamily = font),
        )
        return
    }
    // GitHub Release 본문에는 사용자 섹션 + 개발자 섹션 (`## [X.Y.Z · 개발자 노트]`) 이 함께
    // 담겨온다. 앱은 첫 번째 `## [` 라인 (= 개발자 섹션 시작) 을 delimiter 로 취급해 그 앞까지만
    // 렌더링 — 사용자에게는 사용자 섹션만 노출.
    val userLines = body.lineSequence()
        .takeWhile { !it.trimStart().startsWith("## [") }
        .toList()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        userLines.forEach { rawLine ->
            val line = rawLine.trimEnd('\r').trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(2.dp))

                // `### 신규 기능` (h3) 또는 `## 신규 기능` (h2 · non-bracket, 하위 호환) 을
                // 섹션 헤더로. 브라켓 헤더는 위 takeWhile 로 이미 걸러졌음.
                line.startsWith("### ") || line.startsWith("## ") -> Text(
                    text = line.trimStart('#').trim(),
                    modifier = Modifier.padding(top = 6.dp),
                    style = TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                    ),
                )

                line.startsWith("- ") -> Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "•",
                        style = TextStyle(
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = font,
                        ),
                    )
                    Text(
                        text = line.removePrefix("- "),
                        style = TextStyle(
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = font,
                        ),
                    )
                }

                else -> Text(
                    text = line,
                    style = TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = font,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PrimaryBlue)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "릴리즈 노트를 불러오지 못했어요",
            style = TextStyle(
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = TextStyle(color = TextSecondary, fontSize = 13.sp, fontFamily = font),
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
                color = SurfaceCard,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
    }
}

@Composable
private fun EmptyState() {
    val font = LocalPretendardFontFamily.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "표시할 릴리즈가 없어요",
            style = TextStyle(color = TextSecondary, fontSize = 15.sp, fontFamily = font),
        )
    }
}

// ---------- Preview ----------

@Preview
@Composable
private fun ReleaseNotesScreenPreview() {
    val sample = listOf(
        ReleasesRepository.GithubRelease(
            tagName = "v1.1.0",
            name = "v1.1.0",
            publishedAt = "2026-09-04T08:17:40Z",
            body = """
                ## 신규 기능
                - 카카오 소셜 로그인
                - 프로필 편집 화면

                ## 개선
                - 캘린더 그리드 성능 튜닝
            """.trimIndent(),
        ),
        ReleasesRepository.GithubRelease(
            tagName = "v1.2.0",
            name = "v1.2.0",
            publishedAt = "2026-09-07T08:19:41Z",
            body = """
                ## 신규 기능
                - 자정 위젯 자동 갱신

                ## 버그 수정
                - iOS FCM 토큰 upsert 개선
            """.trimIndent(),
        ),
    )
    LinkerTheme {
        ReleaseNotesScreen(
            releases = sample,
            loading = false,
            error = null,
            onBack = {},
        )
    }
}
