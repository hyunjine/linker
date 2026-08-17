package com.hyunjine.linker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.hyunjine.linker.ui.profile.ProfileSetupScreen
import com.hyunjine.linker.ui.theme.ProvidePretendard

@Composable
@Preview
fun App() {
    MaterialTheme {
        ProvidePretendard {
            ProfileSetupScreen()
        }
    }
}
