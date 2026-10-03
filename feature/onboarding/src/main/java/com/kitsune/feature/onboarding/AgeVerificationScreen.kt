package com.kitsune.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kitsune.feature.onboarding.R

@Composable
fun AgeVerificationScreen(
    onAdultVerified: () -> Unit,
    onUnderage: () -> Unit,
    viewModel: AgeVerificationViewModel = hiltViewModel()
) {
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val incompleteDateError = stringResource(R.string.onboarding_age_error_incomplete)
    val invalidDateError = stringResource(R.string.onboarding_age_error_invalid)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.onboarding_age_verification_message),
            style = MaterialTheme.typography.bodyLarge
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = day,
                onValueChange = { if (it.length <= 2) day = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.onboarding_age_day_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = month,
                onValueChange = { if (it.length <= 2) month = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.onboarding_age_month_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = year,
                onValueChange = { if (it.length <= 4) year = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.onboarding_age_year_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1.2f)
            )
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            onClick = {
                val d = day.toIntOrNull()
                val m = month.toIntOrNull()
                val y = year.toIntOrNull()
                if (d == null || m == null || y == null) {
                    errorMessage = incompleteDateError
                    return@Button
                }
                when (viewModel.submitBirthDate(d, m, y)) {
                    AgeVerificationOutcome.Adult -> onAdultVerified()
                    AgeVerificationOutcome.Underage -> onUnderage()
                    AgeVerificationOutcome.InvalidDate -> errorMessage = invalidDateError
                }
            }
        ) {
            Text(stringResource(R.string.onboarding_age_continue))
        }
    }
}
