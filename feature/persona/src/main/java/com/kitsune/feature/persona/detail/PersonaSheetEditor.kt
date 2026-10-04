package com.kitsune.feature.persona.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.KitsuneButton
import com.kitsune.core.designsystem.component.KitsuneQuietButton
import com.kitsune.core.designsystem.component.KitsuneSheet
import com.kitsune.feature.persona.R

/** The core of a character sheet, as the user edits it. */
data class PersonaSheetDraft(
    val name: String,
    val age: Int,
    val shortDescription: String,
    val personality: String,
    val scenario: String,
    val firstMessage: String,
    val exampleDialogues: String
)

/**
 * Edits what a generated or downloaded character *is*: name, age, description, personality,
 * scenario, opening message and example dialogues. Until 2026-10-04 none of this could be changed
 * after creation — only the inner drives could — which is the opposite of an app built around
 * customisation.
 */
@Composable
fun PersonaSheetEditor(
    persona: PersonaEntity,
    onDismiss: () -> Unit,
    onSave: (PersonaSheetDraft) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(persona.name) }
    var age by rememberSaveable { mutableStateOf(persona.age.toString()) }
    var description by rememberSaveable { mutableStateOf(persona.shortDescription) }
    var personality by rememberSaveable { mutableStateOf(persona.personality) }
    var scenario by rememberSaveable { mutableStateOf(persona.scenario) }
    var firstMessage by rememberSaveable { mutableStateOf(persona.firstMessage) }
    var dialogues by rememberSaveable { mutableStateOf(persona.exampleDialogues) }
    val ageValue = age.toIntOrNull()
    val ageError = ageValue == null || ageValue < 18
    val valid = name.isNotBlank() && !ageError

    KitsuneSheet(onDismiss = onDismiss, title = stringResource(R.string.persona_edit_title)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text(stringResource(R.string.persona_edit_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(KitsuneTheme.spacing.sm))
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.persona_edit_age)) },
                    isError = ageError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(KitsuneTheme.spacing.xxl * 3)
                )
            }
            if (ageError) {
                Text(
                    stringResource(R.string.persona_edit_age_error),
                    style = KitsuneTheme.type.meta,
                    color = KitsuneTheme.colors.error,
                    modifier = Modifier.padding(top = KitsuneTheme.spacing.xs)
                )
            }
            Field(R.string.persona_edit_description, description, 3) { description = it }
            Field(R.string.persona_edit_personality, personality, 4) { personality = it }
            Field(R.string.persona_edit_scenario, scenario, 3) { scenario = it }
            Field(R.string.persona_edit_first_message, firstMessage, 4) { firstMessage = it }
            Field(R.string.persona_edit_dialogues, dialogues, 4) { dialogues = it }
            Spacer(Modifier.height(KitsuneTheme.spacing.lg))
            Row {
                KitsuneQuietButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                KitsuneButton(
                    text = stringResource(R.string.action_save),
                    enabled = valid,
                    onClick = {
                        onSave(
                            PersonaSheetDraft(
                                name = name.trim(),
                                age = ageValue ?: persona.age,
                                shortDescription = description.trim(),
                                personality = personality.trim(),
                                scenario = scenario.trim(),
                                firstMessage = firstMessage.trim(),
                                exampleDialogues = dialogues.trim()
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun Field(label: Int, value: String, minLines: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth().padding(top = KitsuneTheme.spacing.md)
    )
}
