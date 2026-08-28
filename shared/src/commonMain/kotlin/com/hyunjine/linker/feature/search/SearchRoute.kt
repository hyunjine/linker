package com.hyunjine.linker.feature.search

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Nav entry 와 [SearchScreen] 사이의 glue. VM 을 붙여주고 화면 콜백만 상위로 노출.
 */
@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onScheduleClick: (id: String) -> Unit,
    onAnniversaryClick: (id: String) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel { SearchViewModel() }
    SearchScreen(
        onBack = onBack,
        onSearch = { query -> viewModel.search(query) },
        onScheduleClick = onScheduleClick,
        onAnniversaryClick = onAnniversaryClick,
    )
}
