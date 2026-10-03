package com.kitsune.core.designsystem.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kitsune.core.designsystem.R
import kotlinx.coroutines.launch

/**
 * Pop-up de bienvenue affichée au premier lancement de l'application.
 * Carousel de 4 pages avec navigation et indicateurs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeDialog(
    onDismiss: () -> Unit,
    onStartFirstPersona: () -> Unit,
    onOpenPersonalInfo: () -> Unit
) {
    val pageCount = 5
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.designsystem_welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                ) { page ->
                    when (page) {
                        0 -> WelcomePage(
                            icon = Icons.Default.Lock,
                            title = stringResource(R.string.designsystem_welcome_page1_title),
                            description = stringResource(R.string.designsystem_welcome_page1_desc)
                        )
                        1 -> WelcomePage(
                            icon = Icons.Default.Storage,
                            title = stringResource(R.string.designsystem_welcome_page2_title),
                            description = stringResource(R.string.designsystem_welcome_page2_desc)
                        )
                        2 -> WelcomePage(
                            icon = Icons.Default.AutoAwesome,
                            title = stringResource(R.string.designsystem_welcome_page3_title),
                            description = stringResource(R.string.designsystem_welcome_page3_desc)
                        )
                        3 -> WelcomePage(
                            icon = Icons.Default.Badge,
                            title = stringResource(R.string.designsystem_welcome_page_personal_info_title),
                            description = stringResource(R.string.designsystem_welcome_page_personal_info_desc),
                            actionLabel = stringResource(R.string.designsystem_welcome_page_personal_info_action),
                            onAction = onOpenPersonalInfo
                        )
                        4 -> WelcomePage(
                            icon = Icons.Default.Favorite,
                            title = stringResource(R.string.designsystem_welcome_page4_title),
                            description = stringResource(R.string.designsystem_welcome_page4_desc)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Page indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pageCount) { index ->
                        val color = if (pagerState.currentPage == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (pagerState.currentPage < pageCount - 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (pagerState.currentPage > 0) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.designsystem_welcome_prev))
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.designsystem_welcome_next))
                        }
                    }
                } else {
                    // Interactive close to the tutorial (FEATURES.md): the primary action actually
                    // DOES something — starts persona creation — instead of just closing a dialog.
                    // [onStartFirstPersona] is expected to close the dialog itself, same
                    // responsibility split as [onDismiss] for the "later" path below.
                    Button(
                        onClick = onStartFirstPersona,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.designsystem_welcome_start_persona))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.designsystem_welcome_prev))
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.designsystem_welcome_later))
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun WelcomePage(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Écran de guide détaillé affichant plus d'informations sur l'application.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeGuideScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.designsystem_guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.designsystem_guide_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.designsystem_guide_about_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                stringResource(R.string.designsystem_guide_about_body),
                style = MaterialTheme.typography.bodyLarge
            )
            
            HorizontalDivider()
            
            Text(
                stringResource(R.string.designsystem_guide_privacy_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                stringResource(R.string.designsystem_guide_privacy_body),
                style = MaterialTheme.typography.bodyMedium
            )
            
            HorizontalDivider()
            
            Text(
                stringResource(R.string.designsystem_guide_features_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                stringResource(R.string.designsystem_guide_features_body),
                style = MaterialTheme.typography.bodyMedium
            )
            
            HorizontalDivider()
            
            Text(
                stringResource(R.string.designsystem_guide_support_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                stringResource(R.string.designsystem_guide_support_body),
                style = MaterialTheme.typography.bodyMedium
            )
            
            HorizontalDivider()
            
            Text(
                stringResource(R.string.designsystem_guide_contact_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                stringResource(R.string.designsystem_guide_contact_body),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.designsystem_guide_start_button))
            }
        }
    }
}
