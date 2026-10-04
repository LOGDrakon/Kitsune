package com.kitsune.feature.settings.proposals

import com.kitsune.core.designsystem.component.KitsunePage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kitsune.core.backend.model.ProposalResponse
import com.kitsune.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalsScreen(
    onBack: () -> Unit,
    viewModel: ProposalsViewModel = hiltViewModel()
) {
    val proposals by viewModel.proposals.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    KitsunePage(
        title = stringResource(R.string.improve_kitsune_title),
        condensedTitle = true,
        onBack = onBack,
        floatingAction = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.create_proposal_content_description))
            }
        }
    ) { padding ->
        if (proposals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.proposals_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.proposals_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(proposals) { proposal ->
                    ProposalCard(
                        proposal = proposal,
                        onVote = { voteType ->
                            viewModel.voteOnProposal(proposal.id, voteType)
                        }
                    )
                }
            }
        }

        if (showCreateDialog) {
            CreateProposalDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { title, description ->
                    viewModel.createProposal(title, description)
                    showCreateDialog = false
                }
            )
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: ProposalResponse,
    onVote: (VoteType) -> Unit
) {
    val userVote = proposal.myVote?.let { runCatching { VoteType.valueOf(it) }.getOrNull() }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Titre
            Text(
                proposal.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Description
            Text(
                proposal.description,
                style = MaterialTheme.typography.bodyMedium
            )

            // Date
            Text(
                stringResource(R.string.proposal_created_on, proposal.createdAt.take(10)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Votes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score
                Text(
                    stringResource(R.string.proposal_score_label, proposal.upVotes - proposal.downVotes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Boutons de vote
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Up vote
                    IconButton(
                        onClick = { onVote(VoteType.UP) },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (userVote == VoteType.UP) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Default.ThumbUp,
                            stringResource(R.string.vote_up_content_description),
                            tint = if (userVote == VoteType.UP) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        "${proposal.upVotes}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )

                    // Down vote
                    IconButton(
                        onClick = { onVote(VoteType.DOWN) },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (userVote == VoteType.DOWN) 
                                MaterialTheme.colorScheme.errorContainer 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Default.ThumbDown,
                            stringResource(R.string.vote_down_content_description),
                            tint = if (userVote == VoteType.DOWN) 
                                MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        "${proposal.downVotes}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProposalDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_proposal_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.proposal_title_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.proposal_description_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title, description) },
                enabled = title.isNotBlank() && description.isNotBlank()
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
