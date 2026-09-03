package com.hyunjine.linker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    // Android 13+ POST_NOTIFICATIONS 런타임 요청 결과 콜백. 허용/거부 어느 쪽이든
    // 앱 흐름은 그대로 진행. 토큰 라이프사이클은 LinkerFirebaseMessagingService.onNewToken
    // (Firebase 가 rotation 판정 시 자동 호출) 이 담당하므로 여기서 강제 refresh 하지 않음.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Log.d("Linker", "POST_NOTIFICATIONS 권한 결과 granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        maybeRequestNotificationPermission()

        setContent {
            App()
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
