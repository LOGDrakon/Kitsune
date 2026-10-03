package com.kitsune.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.security.onboarding.AgeVerificationStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

private const val MINIMUM_AGE_YEARS = 18

sealed interface AgeVerificationOutcome {
    data object Adult : AgeVerificationOutcome
    data object Underage : AgeVerificationOutcome
    data object InvalidDate : AgeVerificationOutcome
}

sealed interface RegistrationState {
    data object Idle : RegistrationState
    data object Registering : RegistrationState
    data object Done : RegistrationState
    data class Error(val message: String) : RegistrationState
}

@HiltViewModel
class AgeVerificationViewModel @Inject constructor(
    private val ageVerificationStore: AgeVerificationStore,
    private val backendClient: KitsuneBackendClient
) : ViewModel() {

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    fun submitBirthDate(day: Int, month: Int, year: Int): AgeVerificationOutcome {
        val birthDate = try {
            LocalDate.of(year, month, day)
        } catch (e: Exception) {
            return AgeVerificationOutcome.InvalidDate
        }

        if (birthDate.isAfter(LocalDate.now())) {
            return AgeVerificationOutcome.InvalidDate
        }

        val age = Period.between(birthDate, LocalDate.now()).years

        return if (age >= MINIMUM_AGE_YEARS) {
            ageVerificationStore.markVerifiedAdult()
            _registrationState.value = RegistrationState.Registering
            viewModelScope.launch {
                backendClient.registerAnonymous()
                    .onSuccess { _registrationState.value = RegistrationState.Done }
                    .onFailure { _registrationState.value = RegistrationState.Error(it.message ?: "Registration failed") }
            }
            AgeVerificationOutcome.Adult
        } else {
            AgeVerificationOutcome.Underage
        }
    }
}
