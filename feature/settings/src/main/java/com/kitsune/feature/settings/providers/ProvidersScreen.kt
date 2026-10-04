package com.kitsune.feature.settings.providers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.KitsuneButton
import com.kitsune.core.designsystem.component.KitsuneCard
import com.kitsune.core.designsystem.component.KitsuneConfirmDialog
import com.kitsune.core.designsystem.component.KitsuneEmptyState
import com.kitsune.core.designsystem.component.KitsuneFilterChip
import com.kitsune.core.designsystem.component.KitsuneNotice
import com.kitsune.core.designsystem.component.KitsunePage
import com.kitsune.core.designsystem.component.KitsuneQuietButton
import com.kitsune.core.designsystem.component.KitsuneRow
import com.kitsune.core.designsystem.component.KitsuneSecondaryButton
import com.kitsune.core.designsystem.component.KitsuneSheet
import com.kitsune.core.designsystem.component.KitsuneTextField
import com.kitsune.core.designsystem.component.NoticeTone
import com.kitsune.core.designsystem.component.PageTitle
import com.kitsune.core.designsystem.component.SectionHeader
import com.kitsune.core.network.provider.OpenRouterEndpoint
import com.kitsune.core.network.provider.OpenRouterRouting
import com.kitsune.core.network.provider.ProviderConfig
import com.kitsune.core.network.provider.ProviderPreset
import com.kitsune.feature.settings.R
import java.util.Locale

@Composable
fun ProvidersScreen(
    onBack: () -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel()
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()

    val current = draft
    if (current != null) {
        BackHandler { viewModel.closeEditor() }
        ProviderEditor(config = current, viewModel = viewModel)
        return
    }

    var showPresetSheet by remember { mutableStateOf(false) }

    KitsunePage(title = stringResource(R.string.providers_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KitsuneTheme.spacing.gutter)
                .padding(bottom = KitsuneTheme.spacing.scrollBottom)
        ) {
            PageTitle(text = stringResource(R.string.providers_title))
            Text(
                stringResource(R.string.providers_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = KitsuneTheme.colors.textSecondary
            )
            Spacer(Modifier.height(KitsuneTheme.spacing.lg))

            if (providers.isEmpty()) {
                KitsuneEmptyState(
                    title = stringResource(R.string.providers_empty_title),
                    body = stringResource(R.string.providers_empty_body),
                    icon = Icons.Filled.Key,
                    actionLabel = stringResource(R.string.providers_add),
                    onAction = { showPresetSheet = true }
                )
            } else {
                providers.forEachIndexed { index, provider ->
                    KitsuneRow(
                        title = provider.name,
                        subtitle = provider.normalizedBaseUrl,
                        meta = if (index == 0) stringResource(R.string.providers_default_badge) else null,
                        onClick = { viewModel.startEdit(provider.id) },
                        modifier = Modifier.padding(bottom = KitsuneTheme.spacing.sm)
                    )
                }
                Spacer(Modifier.height(KitsuneTheme.spacing.md))
                KitsuneSecondaryButton(
                    text = stringResource(R.string.providers_add),
                    icon = Icons.Filled.Add,
                    onClick = { showPresetSheet = true }
                )
            }
        }
    }

    if (showPresetSheet) {
        KitsuneSheet(onDismiss = { showPresetSheet = false }, title = stringResource(R.string.providers_choose_preset)) {
            ProviderPreset.entries.forEach { preset ->
                KitsuneRow(
                    title = preset.displayName,
                    subtitle = preset.defaultBaseUrl.ifBlank { stringResource(R.string.providers_custom_hint) },
                    card = false,
                    onClick = {
                        showPresetSheet = false
                        viewModel.startAdd(preset)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProviderEditor(config: ProviderConfig, viewModel: ProvidersViewModel) {
    val isNew by viewModel.isNew.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf(false) }
    val isDefault = providers.firstOrNull()?.id == config.id

    KitsunePage(
        title = if (isNew) stringResource(R.string.providers_add_title, config.preset.displayName) else config.name,
        onBack = viewModel::closeEditor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KitsuneTheme.spacing.gutter)
                .padding(bottom = KitsuneTheme.spacing.scrollBottom)
        ) {
            KitsuneTextField(
                value = config.name,
                onValueChange = { v -> viewModel.updateDraft { it.copy(name = v) } },
                label = stringResource(R.string.providers_field_name),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(KitsuneTheme.spacing.md))
            KitsuneTextField(
                value = config.baseUrl,
                onValueChange = { v -> viewModel.updateDraft { it.copy(baseUrl = v.trim()) } },
                label = stringResource(R.string.providers_field_url),
                helper = stringResource(R.string.providers_field_url_helper),
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(KitsuneTheme.spacing.md))
            KitsuneTextField(
                value = config.apiKey,
                onValueChange = { v -> viewModel.updateDraft { it.copy(apiKey = v.trim()) } },
                label = stringResource(R.string.providers_field_key),
                helper = stringResource(
                    if (config.preset.requiresKey) R.string.providers_field_key_helper
                    else R.string.providers_field_key_optional_helper
                ),
                password = true,
                modifier = Modifier.fillMaxWidth()
            )
            config.preset.keyUrl?.let { url ->
                KitsuneQuietButton(
                    text = stringResource(R.string.providers_get_key, config.preset.displayName),
                    onClick = { uriHandler.openUri(url) },
                    accent = true
                )
            }

            Spacer(Modifier.height(KitsuneTheme.spacing.lg))
            KitsuneSecondaryButton(
                text = stringResource(R.string.providers_test),
                loading = test is ConnectionTest.Running,
                enabled = config.baseUrl.isNotBlank(),
                onClick = viewModel::testConnection
            )
            when (val t = test) {
                is ConnectionTest.Success -> KitsuneNotice(
                    text = stringResource(R.string.providers_test_success, t.modelCount),
                    icon = Icons.Filled.CheckCircle,
                    tone = NoticeTone.Success,
                    modifier = Modifier.padding(top = KitsuneTheme.spacing.sm)
                )
                is ConnectionTest.Failure -> KitsuneNotice(
                    text = t.message,
                    icon = Icons.Filled.Warning,
                    tone = NoticeTone.Error,
                    modifier = Modifier.padding(top = KitsuneTheme.spacing.sm)
                )
                else -> Unit
            }

            if (config.preset.isOpenRouter) {
                Spacer(Modifier.height(KitsuneTheme.spacing.xl))
                OpenRouterRoutingEditor(config = config, viewModel = viewModel)
            }

            Spacer(Modifier.height(KitsuneTheme.spacing.xl))
            KitsuneButton(
                text = stringResource(R.string.providers_save),
                enabled = config.baseUrl.isNotBlank() && (!config.preset.requiresKey || config.apiKey.isNotBlank()),
                onClick = viewModel::save
            )
            if (!isNew) {
                if (!isDefault) {
                    KitsuneQuietButton(
                        text = stringResource(R.string.providers_make_default),
                        onClick = { viewModel.makeDefault(config.id) },
                        modifier = Modifier.padding(top = KitsuneTheme.spacing.sm)
                    )
                }
                KitsuneQuietButton(
                    text = stringResource(R.string.providers_delete),
                    onClick = { confirmDelete = true },
                    modifier = Modifier.padding(top = KitsuneTheme.spacing.xs)
                )
            }
        }
    }

    if (confirmDelete) {
        KitsuneConfirmDialog(
            title = stringResource(R.string.providers_delete_confirm_title, config.name),
            body = stringResource(R.string.providers_delete_confirm_body),
            confirmLabel = stringResource(R.string.providers_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(config.id)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/**
 * OpenRouter's provider routing. Each OpenRouter model is usually served by several upstream
 * providers that differ in precision (quantization), speed and reliability; by default OpenRouter
 * picks the cheapest. These settings let the user decide instead.
 */
@Composable
private fun OpenRouterRoutingEditor(config: ProviderConfig, viewModel: ProvidersViewModel) {
    val routing = config.routing
    val endpointsState by viewModel.endpoints.collectAsStateWithLifecycle()

    SectionHeader(title = stringResource(R.string.routing_title))
    Text(
        stringResource(R.string.routing_intro),
        style = MaterialTheme.typography.bodySmall,
        color = KitsuneTheme.colors.textSecondary,
        modifier = Modifier.padding(top = KitsuneTheme.spacing.xs)
    )

    // Simple level: four presets. Advanced level: every OpenRouter parameter, opened automatically when
    // the current routing matches no preset.
    val preset = OpenRouterRouting.presetOf(routing)
    var advanced by remember(config.id) { mutableStateOf(preset == null) }
    Spacer(Modifier.height(KitsuneTheme.spacing.md))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.sm)
    ) {
        listOf(
            OpenRouterRouting.PRESET_FAST to R.string.routing_preset_fast,
            OpenRouterRouting.PRESET_CHEAP to R.string.routing_preset_cheap,
            OpenRouterRouting.PRESET_QUALITY to R.string.routing_preset_quality,
            OpenRouterRouting.PRESET_PRIVATE to R.string.routing_preset_private
        ).forEach { (key, label) ->
            KitsuneFilterChip(
                text = stringResource(label),
                selected = preset == key,
                onClick = { viewModel.updateRouting { OpenRouterRouting.PRESETS.getValue(key) } }
            )
        }
    }
    Text(
        stringResource(
            when (preset) {
                OpenRouterRouting.PRESET_FAST -> R.string.routing_preset_fast_hint
                OpenRouterRouting.PRESET_CHEAP -> R.string.routing_preset_cheap_hint
                OpenRouterRouting.PRESET_QUALITY -> R.string.routing_preset_quality_hint
                OpenRouterRouting.PRESET_PRIVATE -> R.string.routing_preset_private_hint
                else -> R.string.routing_preset_custom_hint
            }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = KitsuneTheme.colors.textDim,
        modifier = Modifier.padding(top = KitsuneTheme.spacing.xs)
    )
    ToggleRow(
        title = stringResource(R.string.routing_advanced),
        subtitle = stringResource(R.string.routing_advanced_hint),
        checked = advanced,
        onChange = { advanced = it }
    )
    if (advanced) {
        Spacer(Modifier.height(KitsuneTheme.spacing.md))
        Text(stringResource(R.string.routing_sort), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = KitsuneTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.sm)
        ) {
            listOf(
                null to R.string.routing_sort_default,
                OpenRouterRouting.SORT_PRICE to R.string.routing_sort_price,
                OpenRouterRouting.SORT_THROUGHPUT to R.string.routing_sort_throughput,
                OpenRouterRouting.SORT_LATENCY to R.string.routing_sort_latency
            ).forEach { (value, label) ->
                KitsuneFilterChip(
                    text = stringResource(label),
                    selected = routing.sort == value,
                    onClick = { viewModel.updateRouting { it.copy(sort = value) } }
                )
            }
        }

        Spacer(Modifier.height(KitsuneTheme.spacing.md))
        Text(stringResource(R.string.routing_quantizations), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.routing_quantizations_hint),
            style = MaterialTheme.typography.bodySmall,
            color = KitsuneTheme.colors.textDim
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = KitsuneTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.sm)
        ) {
            OpenRouterRouting.ALL_QUANTIZATIONS.forEach { q ->
                val selected = q in routing.quantizations
                KitsuneFilterChip(
                    text = q,
                    selected = selected,
                    onClick = {
                        viewModel.updateRouting {
                            it.copy(quantizations = if (selected) it.quantizations - q else it.quantizations + q)
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(KitsuneTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.sm)) {
            KitsuneTextField(
                value = routing.preferredMinThroughput?.let(::formatNumber).orEmpty(),
                onValueChange = { v -> viewModel.updateRouting { it.copy(preferredMinThroughput = v.replace(',', '.').toDoubleOrNull()) } },
                label = stringResource(R.string.routing_min_throughput),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )
            KitsuneTextField(
                value = routing.preferredMaxLatency?.let(::formatNumber).orEmpty(),
                onValueChange = { v -> viewModel.updateRouting { it.copy(preferredMaxLatency = v.replace(',', '.').toDoubleOrNull()) } },
                label = stringResource(R.string.routing_max_latency),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            stringResource(R.string.routing_thresholds_hint),
            style = MaterialTheme.typography.bodySmall,
            color = KitsuneTheme.colors.textDim,
            modifier = Modifier.padding(top = KitsuneTheme.spacing.xs)
        )

        Spacer(Modifier.height(KitsuneTheme.spacing.md))
        KitsuneTextField(
            value = routing.order.joinToString(", "),
            onValueChange = { v -> viewModel.updateRouting { it.copy(order = slugs(v)) } },
            label = stringResource(R.string.routing_order),
            helper = stringResource(R.string.routing_slugs_helper),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(KitsuneTheme.spacing.sm))
        KitsuneTextField(
            value = routing.only.joinToString(", "),
            onValueChange = { v -> viewModel.updateRouting { it.copy(only = slugs(v)) } },
            label = stringResource(R.string.routing_only),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(KitsuneTheme.spacing.sm))
        KitsuneTextField(
            value = routing.ignore.joinToString(", "),
            onValueChange = { v -> viewModel.updateRouting { it.copy(ignore = slugs(v)) } },
            label = stringResource(R.string.routing_ignore),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(KitsuneTheme.spacing.md))
        ToggleRow(
            title = stringResource(R.string.routing_deny_data_collection),
            subtitle = stringResource(R.string.routing_deny_data_collection_hint),
            checked = routing.denyDataCollection,
            onChange = { c -> viewModel.updateRouting { it.copy(denyDataCollection = c) } }
        )
        ToggleRow(
            title = stringResource(R.string.routing_zdr),
            subtitle = stringResource(R.string.routing_zdr_hint),
            checked = routing.zeroDataRetention,
            onChange = { c -> viewModel.updateRouting { it.copy(zeroDataRetention = c) } }
        )
        ToggleRow(
            title = stringResource(R.string.routing_allow_fallbacks),
            subtitle = stringResource(R.string.routing_allow_fallbacks_hint),
            checked = routing.allowFallbacks,
            onChange = { c -> viewModel.updateRouting { it.copy(allowFallbacks = c) } }
        )
        ToggleRow(
            title = stringResource(R.string.routing_require_parameters),
            subtitle = stringResource(R.string.routing_require_parameters_hint),
            checked = routing.requireParameters,
            onChange = { c -> viewModel.updateRouting { it.copy(requireParameters = c) } }
        )

        // Endpoint inspector: what restricting quantization or providers would actually leave.
        Spacer(Modifier.height(KitsuneTheme.spacing.lg))
        SectionHeader(title = stringResource(R.string.routing_endpoints_title))
        var modelId by remember(config.id) { mutableStateOf(viewModel.currentChatModelFor(config.id).orEmpty()) }
        KitsuneTextField(
            value = modelId,
            onValueChange = { modelId = it },
            label = stringResource(R.string.routing_endpoints_model),
            placeholder = "deepseek/deepseek-v4-flash",
            modifier = Modifier.fillMaxWidth().padding(top = KitsuneTheme.spacing.xs)
        )
        KitsuneSecondaryButton(
            text = stringResource(R.string.routing_endpoints_load),
            loading = endpointsState is EndpointsState.Loading,
            enabled = modelId.isNotBlank() && config.apiKey.isNotBlank(),
            onClick = { viewModel.loadEndpoints(modelId) },
            modifier = Modifier.padding(top = KitsuneTheme.spacing.sm)
        )
        when (val e = endpointsState) {
            is EndpointsState.Failure -> KitsuneNotice(
                text = e.message,
                icon = Icons.Filled.Warning,
                tone = NoticeTone.Error,
                modifier = Modifier.padding(top = KitsuneTheme.spacing.sm)
            )
            is EndpointsState.Loaded -> {
                if (e.endpoints.isEmpty()) {
                    KitsuneNotice(
                        text = stringResource(R.string.routing_endpoints_none),
                        icon = Icons.Filled.Info,
                        modifier = Modifier.padding(top = KitsuneTheme.spacing.sm)
                    )
                }
                e.endpoints.forEach { endpoint ->
                    EndpointCard(
                        endpoint = endpoint,
                        routing = routing,
                        onPrefer = { slug -> viewModel.updateRouting { it.copy(order = (it.order - slug) + slug, ignore = it.ignore - slug) } },
                        onExclude = { slug -> viewModel.updateRouting { it.copy(ignore = (it.ignore - slug) + slug, order = it.order - slug, only = it.only - slug) } }
                    )
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun EndpointCard(
    endpoint: OpenRouterEndpoint,
    routing: OpenRouterRouting,
    onPrefer: (String) -> Unit,
    onExclude: (String) -> Unit
) {
    val slug = endpoint.providerSlug
    val excludedByQuant = routing.quantizations.isNotEmpty() &&
        (endpoint.quantization ?: "unknown") !in routing.quantizations
    KitsuneCard(modifier = Modifier.fillMaxWidth().padding(top = KitsuneTheme.spacing.sm)) {
        Text(endpoint.providerName, style = MaterialTheme.typography.titleSmall)
        val facts = buildList {
            add(stringResource(R.string.routing_endpoint_quant, endpoint.quantization ?: "?"))
            endpoint.throughputTokensPerSecond?.let { add(stringResource(R.string.routing_endpoint_throughput, formatNumber(it))) }
            endpoint.latencySeconds?.let { add(stringResource(R.string.routing_endpoint_latency, formatNumber(it))) }
            endpoint.uptimePercent?.let { add(stringResource(R.string.routing_endpoint_uptime, formatNumber(it))) }
            if (endpoint.promptPricePerMillion != null && endpoint.completionPricePerMillion != null) {
                add(stringResource(R.string.routing_endpoint_price, formatNumber(endpoint.promptPricePerMillion!!), formatNumber(endpoint.completionPricePerMillion!!)))
            }
            endpoint.contextLength?.let { add(stringResource(R.string.routing_endpoint_context, it / 1000)) }
        }
        Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = KitsuneTheme.colors.textSecondary)
        if (excludedByQuant) {
            Text(
                stringResource(R.string.routing_endpoint_excluded_by_quant),
                style = MaterialTheme.typography.bodySmall,
                color = KitsuneTheme.colors.warn
            )
        }
        if (slug != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                KitsuneQuietButton(
                    text = stringResource(if (slug in routing.order) R.string.routing_endpoint_preferred else R.string.routing_endpoint_prefer),
                    onClick = { onPrefer(slug) },
                    accent = slug !in routing.order
                )
                KitsuneQuietButton(
                    text = stringResource(if (slug in routing.ignore) R.string.routing_endpoint_excluded else R.string.routing_endpoint_exclude),
                    onClick = { onExclude(slug) }
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    com.kitsune.core.designsystem.component.KitsuneSwitchRow(
        title = title, description = subtitle, checked = checked, onCheckedChange = onChange
    )

private fun slugs(raw: String): List<String> =
    raw.split(',', ' ', '\n').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct()

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
