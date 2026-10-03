package com.kitsune.core.models

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.network.catalog.ModelInfo

@Composable
fun ModelPickerDialog(
    capability: ModelCapabilityFilter,
    onDismiss: () -> Unit,
    onModelSelected: (String) -> Unit,
    title: String? = null,
    viewModel: ModelCatalogViewModel = hiltViewModel()
) {
    val allModels by viewModel.displayedModels.collectAsStateWithLifecycle()
    val models = allModels.filter(capability::matches)
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val contextFilter by viewModel.contextFilter.collectAsStateWithLifecycle()
    val costFilter by viewModel.costFilter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val resolvedTitle = title ?: stringResource(capability.titleRes)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(16.dp)) {
                Text(resolvedTitle, style = MaterialTheme.typography.titleLarge)

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelSortOption.entries.forEach { option ->
                        FilterChip(
                            selected = sortOption == option,
                            onClick = { viewModel.setSortOption(option) },
                            label = {
                                val sortLabel = stringResource(option.labelRes)
                                Text(stringResource(R.string.model_picker_sort_label, sortLabel))
                            }
                        )
                    }
                }

                // Combined filtering: context and cost filters both apply at the same time,
                // independently of each other and of the sort above.
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelContextFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = contextFilter == filter,
                            onClick = { viewModel.setContextFilter(filter) },
                            label = { Text(stringResource(filter.labelRes)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelCostFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = costFilter == filter,
                            onClick = { viewModel.setCostFilter(filter) },
                            label = { Text(stringResource(filter.labelRes)) }
                        )
                    }
                }

                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    error != null -> Column(modifier = Modifier.padding(top = 16.dp)) {
                        val errorText = error.orEmpty().ifEmpty { stringResource(R.string.model_catalog_unknown_error) }
                        Text(stringResource(R.string.model_picker_error_loading, errorText), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.refresh(forceRefresh = true) }, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.model_picker_retry))
                        }
                    }

                    models.isEmpty() -> Text(
                        stringResource(R.string.model_picker_no_results),
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    else -> LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                        items(models, key = ModelInfo::id) { model ->
                            ListItem(
                                headlineContent = { Text(model.id) },
                                supportingContent = { Text(modelSubtitle(model)) },
                                modifier = Modifier.clickable {
                                    onModelSelected(model.id)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
