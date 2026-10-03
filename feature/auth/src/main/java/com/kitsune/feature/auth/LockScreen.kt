package com.kitsune.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.security.model.VaultSecurityMode
import com.kitsune.feature.auth.R

@Composable
fun LockScreen(
    onUnlockedReal: () -> Unit,
    onUnlockedPanic: () -> Unit,
    viewModel: LockScreenViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as FragmentActivity
    val state by viewModel.state.collectAsStateWithLifecycle()
    val securityMode by viewModel.securityMode.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    val isBiometricOnly = securityMode == VaultSecurityMode.BIOMETRIC_ONLY

    LaunchedEffect(state) {
        when (state) {
            LockUiState.UnlockedReal -> onUnlockedReal()
            LockUiState.UnlockedPanic -> onUnlockedPanic()
            else -> Unit
        }
    }

    // Biometric-only mode has no PIN entry point at all — trigger the system prompt as soon as we
    // know that's the current mode, instead of making the user tap a button first.
    LaunchedEffect(isBiometricOnly) {
        if (isBiometricOnly && state is LockUiState.Idle) {
            viewModel.unlock(activity, CharArray(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.auth_lock_title), style = MaterialTheme.typography.headlineSmall)

        if (!isBiometricOnly) {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.auth_lock_pin_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )
        }

        val currentState = state
        if (currentState is LockUiState.Error) {
            Text(
                text = currentState.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            enabled = state !is LockUiState.Loading,
            onClick = { viewModel.unlock(activity, pin.toCharArray()) }
        ) {
            if (state is LockUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text(if (isBiometricOnly) stringResource(R.string.auth_lock_unlock_biometric) else stringResource(R.string.auth_lock_unlock))
        }
    }
}
