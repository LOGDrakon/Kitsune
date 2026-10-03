package com.kitsune.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kitsune.feature.onboarding.R

@Composable
fun RecoverAccountScreen(
    onRecovered: () -> Unit,
    viewModel: RecoverAccountViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as FragmentActivity
    val state by viewModel.state.collectAsStateWithLifecycle()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.onQrScanned(it) }
    }

    LaunchedEffect(state) {
        if (state is RecoverAccountState.Completed) onRecovered()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        when (val current = state) {
            RecoverAccountState.Scanning -> {
                Text(stringResource(R.string.onboarding_recover_scan_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.onboarding_recover_scan_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )
                Button(
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setBeepEnabled(false)
                                .setOrientationLocked(true)
                                .setPrompt(activity.getString(R.string.onboarding_recover_scan_prompt))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.onboarding_recover_scan_button))
                }
            }

            RecoverAccountState.Downloading -> ProgressStep(stringResource(R.string.onboarding_recover_downloading))

            is RecoverAccountState.AwaitingTransferPin -> {
                var pin by remember { mutableStateOf("") }
                Text(stringResource(R.string.onboarding_recover_transfer_pin_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.onboarding_recover_transfer_pin_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.onboarding_pin_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                current.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                Button(
                    onClick = { viewModel.submitTransferPin(pin.toCharArray()) },
                    enabled = pin.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text(stringResource(R.string.onboarding_recover_transfer_pin_confirm))
                }
            }

            RecoverAccountState.Decrypting -> ProgressStep(stringResource(R.string.onboarding_recover_decrypting))

            RecoverAccountState.AwaitingVaultPin -> {
                var pin by remember { mutableStateOf("") }
                var confirmPin by remember { mutableStateOf("") }
                val mismatch = confirmPin.isNotEmpty() && pin != confirmPin
                Text(stringResource(R.string.onboarding_recover_vault_pin_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.onboarding_recover_vault_pin_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.onboarding_pin_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.onboarding_pin_confirm_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (mismatch) {
                    Text(
                        stringResource(R.string.onboarding_recover_pins_do_not_match),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Button(
                    onClick = { viewModel.submitVaultPin(activity, pin.toCharArray()) },
                    enabled = pin.length >= 4 && pin == confirmPin,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text(stringResource(R.string.onboarding_pin_validate))
                }
            }

            RecoverAccountState.Importing -> ProgressStep(stringResource(R.string.onboarding_recover_importing))

            RecoverAccountState.Completed -> Unit

            is RecoverAccountState.Error -> {
                Text(
                    stringResource(R.string.onboarding_recover_error_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ProgressStep(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        CircularProgressIndicator()
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
    }
}
