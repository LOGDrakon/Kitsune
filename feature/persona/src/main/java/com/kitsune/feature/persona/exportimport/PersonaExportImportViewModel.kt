package com.kitsune.feature.persona.exportimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.feature.persona.R
import com.kitsune.feature.persona.card.ExportCharacterCardUseCase
import com.kitsune.feature.persona.card.ImportCharacterCardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PersonaExportImportUiState {
    data object Idle : PersonaExportImportUiState
    data object Exporting : PersonaExportImportUiState
    data object Importing : PersonaExportImportUiState
    data class Success(val message: String) : PersonaExportImportUiState
    data class Error(val message: String) : PersonaExportImportUiState
}

@HiltViewModel
class PersonaExportImportViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val exportUseCase: PersonaExportUseCase,
    private val importUseCase: PersonaImportUseCase,
    private val exportCardUseCase: ExportCharacterCardUseCase,
    private val importCardUseCase: ImportCharacterCardUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PersonaExportImportUiState>(PersonaExportImportUiState.Idle)
    val uiState: StateFlow<PersonaExportImportUiState> = _uiState.asStateFlow()

    /** BUG-004 (BUGS.md): this used to be a bare `suspend fun`, called from the screen's own
     * `rememberCoroutineScope()` rather than [viewModelScope] — an exception thrown while writing
     * to [uri] (revoked URI permission, storage failure) had nowhere supervised to land and would
     * crash whatever scope happened to be running it, tied to the Composable's lifecycle rather
     * than the ViewModel's. Owning the launch here, like every other ViewModel in this codebase,
     * plus a `runCatching` around the actual file write, turns that into a normal [Error] state. */
    /**
     * Writes the persona as a standard character card (2026-08-25).
     *
     * Same `viewModelScope` ownership and same I/O guarding as [exportToFile] — see BUG-004 in its
     * doc comment for why that matters here rather than in the screen's own scope.
     */
    fun exportCardToFile(context: Context, uri: Uri, personaId: String) {
        viewModelScope.launch {
            _uiState.value = PersonaExportImportUiState.Exporting
            runCatching {
                val bytes = exportCardUseCase(personaId)
                    ?: error(context.getString(R.string.card_export_needs_avatar))
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("Impossible d'ouvrir le fichier de destination")
                }
            }.onSuccess {
                _uiState.value = PersonaExportImportUiState.Success(context.getString(R.string.card_export_success))
            }.onFailure { e ->
                _uiState.value = PersonaExportImportUiState.Error(e.message.orEmpty())
            }
        }
    }

    /**
     * Reads a character card and adds it to the library.
     *
     * The age passed here is the app's own 18+ floor rather than anything from the file: the card
     * spec carries no age at all, and this app never infers one. The imported persona lands with that
     * minimum and the user adjusts it on the persona sheet like any other.
     */
    fun importCardFromFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = PersonaExportImportUiState.Importing
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Impossible d'ouvrir le fichier")
                }
                importCardUseCase(bytes, age = MINIMUM_IMPORTED_AGE, maturityTags = emptyList())
                    ?: error(context.getString(R.string.card_import_not_a_card))
            }.onSuccess {
                _uiState.value = PersonaExportImportUiState.Success(context.getString(R.string.card_import_success))
            }.onFailure { e ->
                _uiState.value = PersonaExportImportUiState.Error(e.message.orEmpty())
            }
        }
    }

    fun exportToFile(context: Context, uri: Uri, personaId: String) {
        viewModelScope.launch {
            _uiState.value = PersonaExportImportUiState.Exporting
            exportUseCase.exportPersona(personaId)
                .mapCatching { json ->
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(json.toByteArray())
                        } ?: error("Impossible d'ouvrir le fichier de destination")
                    }
                }
                .onSuccess {
                    _uiState.value = PersonaExportImportUiState.Success(context.getString(R.string.persona_export_success_message))
                }
                .onFailure { e ->
                    _uiState.value = PersonaExportImportUiState.Error(context.getString(R.string.persona_export_error_format, e.message ?: ""))
                }
        }
    }

    /** Same BUG-004 reasoning as [exportToFile] — see its doc comment. [PersonaImportUseCase]
     * already wraps its whole body in `runCatching`, so unlike [exportToFile] there's no raw I/O
     * here left to guard directly — only the missing [viewModelScope] ownership to fix. */
    fun importFromJson(json: String) {
        viewModelScope.launch {
            _uiState.value = PersonaExportImportUiState.Importing
            importUseCase.importPersona(json)
                .onSuccess { personaId ->
                    _uiState.value = PersonaExportImportUiState.Success(appContext.getString(R.string.persona_import_success_format, personaId))
                }
                .onFailure { e ->
                    _uiState.value = PersonaExportImportUiState.Error(appContext.getString(R.string.persona_import_error_format, e.message ?: ""))
                }
        }
    }

    fun resetState() {
        _uiState.value = PersonaExportImportUiState.Idle
    }
}

/** The app's hard floor. Cards carry no age, and Kitsune never infers one. */
private const val MINIMUM_IMPORTED_AGE = 18
