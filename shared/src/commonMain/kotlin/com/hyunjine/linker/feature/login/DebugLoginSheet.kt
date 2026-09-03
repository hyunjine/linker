package com.hyunjine.linker.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DEBUG 빌드에서만 노출되는 email/password 테스트 로그인 시트.
 * Release 빌드는 [com.hyunjine.linker.platform.DebugConfig.enabled] = false 라 진입 자체가 막힘.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebugLoginSheet(
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (email: String, password: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    // 매번 타이핑 안 하도록 세션 테스트 계정 미리 프리필. Debug 빌드에서만 도달하므로 노출 위험 없음.
    var email by remember { mutableStateOf("thevlakk1@gmail.com") }
    var password by remember { mutableStateOf("dhfl265213!") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "[DEBUG] 테스트 로그인",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Text(
                    text = error,
                    style = TextStyle(fontSize = 12.sp, color = Color(0xFFD03A3A)),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("취소") }
                Spacer(Modifier.size(8.dp))
                TextButton(
                    enabled = email.isNotBlank() && password.isNotBlank(),
                    onClick = { onSubmit(email, password) },
                ) { Text("로그인") }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}
