package com.kitsune.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.kitsune.app.navigation.AppEntryViewModel
import com.kitsune.app.navigation.KitsuneNavHost
import com.kitsune.app.navigation.Routes
import com.kitsune.core.backend.AccountLockReason
import com.kitsune.core.backend.AnnouncementManager
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.UserMessageManager
import com.kitsune.core.designsystem.AnnouncementDialog
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.UserMessageDialog
import com.kitsune.core.security.lock.AutoLockManager
import com.kitsune.core.security.storage.SecureStorage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var autoLockManager: AutoLockManager

    @Inject
    lateinit var secureStorage: SecureStorage

    @Inject
    lateinit var announcementManager: AnnouncementManager

    @Inject
    lateinit var userMessageManager: UserMessageManager

    @Inject
    lateinit var backendClient: KitsuneBackendClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (secureStorage.getInt(SecureStorage.KEY_FLAG_SECURE_ENABLED, 1) == 1) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        setContent {
            var discreetMode by remember { mutableStateOf(
                secureStorage.getInt(SecureStorage.KEY_DISCREET_MODE_ENABLED, 0) == 1
            ) }
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    discreetMode = secureStorage.getInt(SecureStorage.KEY_DISCREET_MODE_ENABLED, 0) == 1
                }
            }

            KitsuneTheme(discreet = discreetMode) {
                Surface {
                    val entryViewModel: AppEntryViewModel = hiltViewModel()
                    val startRoute by entryViewModel.startRoute.collectAsStateWithLifecycle()

                    val resolvedRoute = startRoute
                    val accountLockReason by backendClient.accountLockReason.collectAsStateWithLifecycle()

                    if (resolvedRoute == null) {
                        Box(modifier = Modifier.fillMaxSize())
                    } else if (accountLockReason == AccountLockReason.FROZEN) {
                        // FROZEN is a full lockout (temporary hold pending investigation) — BANNED is
                        // deliberately NOT gated here: a banned user keeps normal app access (their
                        // chats, settings, account id) and is only blocked from generating new content,
                        // enforced server-side (checkAccountStatus) — see BUGS.md.
                        AccountLockedScreen(reason = accountLockReason!!)
                    } else {
                        val navController = rememberNavController()
                        var everUnlocked by remember { mutableStateOf(false) }
                        val isLocked by autoLockManager.isLocked.collectAsStateWithLifecycle()

                        LaunchedEffect(isLocked) {
                            if (!isLocked) {
                                everUnlocked = true
                            } else if (everUnlocked) {
                                val persistentDiscreet = secureStorage.getInt(SecureStorage.KEY_DISCREET_MODE_ENABLED, 0) == 1
                                if (!persistentDiscreet) {
                                    discreetMode = false
                                }
                                navController.navigate(Routes.LOCK) { popUpTo(0) }
                            }
                        }

                        KitsuneNavHost(
                            startRoute = resolvedRoute,
                            navController = navController
                        )
                    }

                    val pendingAnnouncements by announcementManager.pendingAnnouncements.collectAsStateWithLifecycle()
                    val coroutineScope = rememberCoroutineScope()
                    val current = pendingAnnouncements.firstOrNull()
                    if (current != null) {
                        AnnouncementDialog(
                            title = current.title,
                            body = current.body,
                            type = current.type,
                            dismissible = current.dismissible,
                            rewardCredits = current.rewardCredits,
                            onDismiss = {
                                coroutineScope.launch { announcementManager.dismiss(current.id) }
                            }
                        )
                    } else {
                        // Only one popup at a time — an unread moderation message waits behind any
                        // pending announcement rather than stacking dialogs.
                        val messages by userMessageManager.messages.collectAsStateWithLifecycle()
                        val currentMessage = messages.firstOrNull { it.readAt == null }
                        if (currentMessage != null) {
                            UserMessageDialog(
                                subject = currentMessage.subject,
                                body = currentMessage.body,
                                onDismiss = {
                                    coroutineScope.launch { userMessageManager.markRead(currentMessage.id) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun AccountLockedScreen(reason: AccountLockReason) {
    val (title, body) = when (reason) {
        AccountLockReason.BANNED -> stringResource(R.string.account_banned_title) to
            stringResource(R.string.account_banned_body)
        AccountLockReason.FROZEN -> stringResource(R.string.account_suspended_title) to
            stringResource(R.string.account_suspended_body)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}
