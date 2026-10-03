package com.kitsune.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsune.feature.onboarding.R

/** First-launch fork, right after language selection: start fresh, or recover an existing
 * account/local data from another device via QR transfer (FEATURES.md section 2). */
@Composable
fun AccountChoiceScreen(
    onCreateNewAccount: () -> Unit,
    onRecoverAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.onboarding_account_choice_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.onboarding_account_choice_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        Button(onClick = onCreateNewAccount, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_account_choice_new))
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onRecoverAccount, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_account_choice_recover))
        }
    }
}
