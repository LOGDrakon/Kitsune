package com.kitsune.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The shell's own small amount of state: the welcome bonus, and the daily Ofuda grant.
 *
 * Both used to be **modal dialogs on launch**. v1 opened with a `ClaimFreeCreditsDialog` the user had
 * to tap through to accept a gift, and a second dialog announcing the daily grant, before they could
 * see their own stories. A dialog is the app interrupting the user; a gift does not need consent, and
 * a daily top-up does not need an acknowledgement.
 *
 * So the bonus is claimed silently the first time the shell is composed, and the grant is surfaced as
 * a quiet in-page notice that the user can ignore entirely.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val backendClient: KitsuneBackendClient
) : ViewModel() {

    /** Non-null while the daily grant has been paid but not yet shown. */
    val pendingDailyGrant: StateFlow<Int?> = backendClient.pendingDailyGrant

    init {
        viewModelScope.launch {
            // The balance refresh is also what triggers the lazy monthly/daily grant server-side
            // (`CreditService.grantMonthlyKitsunePlusCreditsIfDue`), so it has to happen before the
            // grant notice can have anything to show.
            backendClient.refreshBalance()

            // `hasClaimedFreeCredits` is populated by that refresh. The claim endpoint is itself
            // idempotent per account (see `CreditService.claimFreeCredits`), so racing it is safe —
            // the check is only here to avoid a pointless round-trip on every launch.
            if (!backendClient.hasClaimedFreeCredits.value) {
                backendClient.claimFreeCredits()
            }
        }
    }

    fun acknowledgeDailyGrant() = backendClient.acknowledgeDailyGrant()
}
