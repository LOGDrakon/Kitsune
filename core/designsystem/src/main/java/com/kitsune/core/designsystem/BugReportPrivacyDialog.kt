package com.kitsune.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Shown while a bug report is being redacted, encrypted and sent — deliberately has no dismiss
 * action. The caller keeps this visible for a minimum duration regardless of how fast the network
 * call completes, so the privacy message actually gets read rather than reflexively tapped away.
 */
@Composable
fun BugReportPrivacyDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(stringResource(R.string.designsystem_bug_report_sending_title)) },
        text = {
            Column {
                Text(stringResource(R.string.designsystem_bug_report_sending_message))
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    )
}
