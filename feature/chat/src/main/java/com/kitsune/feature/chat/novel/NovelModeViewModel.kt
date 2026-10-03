package com.kitsune.feature.chat.novel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStream
import javax.inject.Inject

sealed class PdfExportState {
    data object Idle : PdfExportState()
    data object Exporting : PdfExportState()
    data object Success : PdfExportState()
    data class Error(val message: String) : PdfExportState()
}

@HiltViewModel
class NovelModeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val personaRepository: PersonaRepository,
    private val exportNovelPdfUseCase: ExportNovelPdfUseCase
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    val messages: StateFlow<List<MessageEntity>> = messageRepository.observeByChat(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _personaName = MutableStateFlow("")
    val personaName: StateFlow<String> = _personaName


    private val _exportState = MutableStateFlow<PdfExportState>(PdfExportState.Idle)
    val exportState: StateFlow<PdfExportState> = _exportState.asStateFlow()

    init {
        viewModelScope.launch {
            val chat = chatRepository.getById(chatId)
            val personaId = chat?.personaId
            if (personaId != null) {
                val persona = personaRepository.getById(personaId)
                _personaName.value = persona?.name ?: ""
            }
        }
    }

    fun exportPdf(output: OutputStream) {
        viewModelScope.launch {
            _exportState.value = PdfExportState.Exporting
            runCatching {
                output.use { exportNovelPdfUseCase.export(chatId, it) }
            }.onSuccess {
                _exportState.value = PdfExportState.Success
            }.onFailure { e ->
                _exportState.value = PdfExportState.Error(e.message ?: "Échec de l'export")
            }
        }
    }

    fun resetExportState() {
        _exportState.value = PdfExportState.Idle
    }
}
