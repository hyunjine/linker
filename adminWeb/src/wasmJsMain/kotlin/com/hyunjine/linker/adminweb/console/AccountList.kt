package com.hyunjine.linker.adminweb.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.adminweb.data.Account
import com.hyunjine.linker.adminweb.data.Platform
import com.hyunjine.linker.adminweb.ui.CheckIcon
import com.hyunjine.linker.adminweb.ui.Colors
import com.hyunjine.linker.adminweb.ui.SearchIcon

/**
 * 좌측 계정 리스트 패널.
 *
 * 최상단 검색 → "모두 선택" 행 → 계정 rows (스크롤). 상태는 [ConsoleState] 가 소유하며 이 컴포저블은
 * 순수 렌더링만 담당한다.
 *
 * @param state 콘솔 화면 전체 상태 홀더.
 * @param modifier 패널을 감싸는 외부 modifier — 상위에서 사이즈/위치 배정.
 */
@Composable
fun AccountList(
    state: ConsoleState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Colors.Surface)
            .padding(vertical = 16.dp),
    ) {
        // 검색 인풋 — 좌우 여백 20 (§Figma 3994:16040 · 386 wide 는 420-2*17 근사).
        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
            SearchField(
                value = state.search,
                onValueChange = { state.search = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        SelectAllRow(
            checked = state.allFilteredSelected,
            count = state.filteredAccounts.size,
            onToggle = { state.toggleAllFiltered() },
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Colors.Divider),
        )

        when (val load = state.loadState) {
            is ConsoleState.LoadState.Loading -> LoadingRow()
            is ConsoleState.LoadState.Failed -> ErrorRow(message = load.message, onRetry = { state.refresh() })
            is ConsoleState.LoadState.Loaded -> {
                val filtered = state.filteredAccounts
                if (filtered.isEmpty()) {
                    EmptyRow(hasSearch = state.search.isNotBlank())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(filtered, key = { it.id }) { account ->
                            AccountRow(
                                account = account,
                                checked = account.id in state.selectedIds,
                                onToggle = { state.toggle(account.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 닉네임 검색 인풋. Compose Material3 의 TextField 대신 얇은 커스텀 — 디자인 컬러/모서리 반경을
 * 정확히 맞추기 위해서.
 *
 * @param value 현재 검색어.
 * @param onValueChange 사용자가 입력할 때 호출.
 * @param modifier 외부 modifier.
 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(37.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Colors.FieldFill)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // wasmJs Skia 는 이모지 폰트 접근이 없어 `🔍` 은 tofu 로 뜬다. 원시 Canvas 로 대체.
            SearchIcon(
                modifier = Modifier.size(14.dp),
                color = Colors.TextTertiary,
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = "닉네임 검색",
                        color = Colors.TextTertiary,
                        fontSize = 14.sp,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = Colors.TextPrimary,
                        fontSize = 14.sp,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * "모두 선택" 행 — 16×16 체크박스 + 라벨 + 오른쪽 카운트.
 *
 * @param checked 현재 필터된 계정이 모두 선택돼 있는지.
 * @param count 필터된 계정 수 — 라벨 옆에 회색으로 표시.
 * @param onToggle 사용자가 이 행을 탭 했을 때 호출.
 */
@Composable
private fun SelectAllRow(
    checked: Boolean,
    count: Int,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallCheckbox(checked = checked)
        Text(
            text = "모두 선택",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Colors.TextPrimary,
        )
        Spacer(Modifier.width(0.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = "($count)",
                fontSize = 12.sp,
                color = Colors.TextSecondary,
            )
        }
    }
}

/**
 * 계정 한 행 — 20×20 체크박스 자리 + 32×32 아바타 (이니셜) + 닉네임 + 플랫폼 배지들.
 *
 * @param account 표시할 계정.
 * @param checked 현재 선택 상태.
 * @param onToggle 행 탭 시 호출 — 선택 토글.
 */
@Composable
fun AccountRow(
    account: Account,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(63.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            RowCheckbox(checked = checked)
        }
        Avatar(account = account)
        Text(
            text = account.nickname.ifBlank { "(이름 없음)" },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Colors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // §9 결정 — iOS · Android 둘 다면 배지 둘 다 표시. 순서는 iOS → Android.
            if (Platform.iOS in account.platforms) {
                PlatformBadge(Platform.iOS)
            }
            if (Platform.Android in account.platforms) {
                PlatformBadge(Platform.Android)
            }
        }
    }
}

/** 아바타 — profileImageUrl 이 있어도 이미지 로더 의존을 피해 이니셜 폴백만 렌더. */
@Composable
private fun Avatar(account: Account) {
    val initial = account.nickname.trim().firstOrNull()?.toString().orEmpty().ifEmpty { "?" }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Colors.NeutralFill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Colors.TextSecondary,
        )
    }
}

/** 20×20 체크박스 — 계정 행용. 체크시 파란 채움 + 흰 체크 아이콘. */
@Composable
private fun RowCheckbox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) Colors.PrimaryBlue else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (checked) Colors.PrimaryBlue else Colors.TextTertiary,
                shape = RoundedCornerShape(4.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            CheckIcon(
                modifier = Modifier.size(14.dp),
                color = Colors.OnPrimary,
                strokeWidthDp = 2.dp,
            )
        }
    }
}

/** 16×16 체크박스 — "모두 선택" 행용. 크기만 다르고 스타일은 [RowCheckbox] 와 동일 톤. */
@Composable
private fun SmallCheckbox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) Colors.PrimaryBlue else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (checked) Colors.PrimaryBlue else Colors.TextTertiary,
                shape = RoundedCornerShape(4.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            CheckIcon(
                modifier = Modifier.size(11.dp),
                color = Colors.OnPrimary,
                strokeWidthDp = 1.6.dp,
            )
        }
    }
}

/** iOS · Android 배지. */
@Composable
private fun PlatformBadge(platform: Platform) {
    val (bg, textColor, label) = when (platform) {
        Platform.iOS -> Triple(Colors.PlatformIOSBg, Colors.PlatformIOSText, "iOS")
        Platform.Android -> Triple(Colors.PlatformAndroidBg, Colors.PlatformAndroidText, "Android")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

/** 계정 로딩 중 표시 — 스피너 하나만. */
@Composable
private fun LoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = Colors.PrimaryBlue,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 계정 조회 실패 시 인라인 재시도 뷰. */
@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = Colors.Danger,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Colors.PrimaryBlue.copy(alpha = 0.1f))
                .clickable(onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "다시 시도",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Colors.PrimaryBlue,
            )
        }
    }
}

/** 계정이 없거나 검색 결과가 없을 때. */
@Composable
private fun EmptyRow(hasSearch: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (hasSearch) "검색 결과가 없어요." else "푸시 가능한 계정이 없어요.",
            fontSize = 13.sp,
            color = Colors.TextSecondary,
        )
    }
}

