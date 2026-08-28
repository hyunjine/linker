package com.hyunjine.linker.feature.anniversary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AnniversariesRoute(onBack: () -> Unit) {
    val viewModel: AnniversariesViewModel = viewModel { AnniversariesViewModel() }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    AnniversariesScreen(
        items = ui.items,
        busy = ui.busy,
        onBack = onBack,
        onAdd = viewModel::add,
        onDelete = viewModel::delete,
    )
}
