package com.cdnhunter.app.ui

import com.google.firebase.auth.FirebaseAuth

/**
 * Single source of truth for the account / subscription facts shown by Settings' [AccountCard]
 * and [ProfileScreen]. Both screens read one [AccountUiState] so the identity shown is the
 * actually-signed-in user and the two screens can never drift apart.
 *
 * Real vs placeholder is kept explicit on purpose:
 *  - displayName / email / initials / emailVerified are REAL — derived from [FirebaseAuth].
 *  - [plan] and [subscription] are PLACEHOLDER: the app has no billing/entitlement backend yet.
 *    They are driven from the two flags below ([PLACEHOLDER_PLAN] / [PLACEHOLDER_SUBSCRIPTION])
 *    instead of literals scattered across screens, so wiring them to a real backend is a single,
 *    single-site change.
 *  - [payments] is a real (currently always empty) list — there is no transaction backend, so
 *    it stays empty and the UI shows a clean empty state until one is wired in.
 */

/** Which plan the account is on. Free vs Pro is the only distinction the UI needs today. */
enum class PlanTier(val label: String) {
    FREE("Free"),
    PRO("Pro"),
}

/**
 * Subscription / entitlement window.
 *
 * [None] is the honest neutral state for when no billing backend has told us anything — the UI
 * renders it WITHOUT inventing renewal dates or day counts. [Active] carries a real window once
 * a backend actually provides one.
 */
sealed interface SubscriptionState {
    data object None : SubscriptionState
    data class Active(
        val daysRemaining: Int,
        val daysTotal: Int,
        val renewalLabel: String,
    ) : SubscriptionState {
        /** Progress of the current billing period, 0f..1f, for Profile's plan meter. */
        val periodProgress: Float
            get() = if (daysTotal <= 0) 0f else (daysRemaining.toFloat() / daysTotal).coerceIn(0f, 1f)

        /** "21 of 30 days remaining" — the long form shown on Profile's plan card. */
        val daysRemainingLabel: String
            get() = "$daysRemaining of $daysTotal days remaining"
    }
}

/** One billing transaction. No backend yet, so the list is empty and the UI shows an empty state. */
data class PaymentRecord(
    val id: String,
    val description: String,
    val amountLabel: String,
    val dateLabel: String,
)

data class AccountUiState(
    val displayName: String,
    val email: String,
    val initials: String,
    val emailVerified: Boolean,
    val plan: PlanTier,
    val subscription: SubscriptionState,
    val payments: List<PaymentRecord>,
) {
    val isPro: Boolean get() = plan == PlanTier.PRO
}

/**
 * The single place plan/subscription placeholders live. When a real entitlement backend exists,
 * derive these from it (or from the Firebase user's custom claims) and every screen updates.
 *
 * Default is FREE with no active subscription: honest, since there is no billing yet nobody is
 * actually paying, and it lets the Free-tier "upgrade to Pro" UI be exercised.
 */
private val PLACEHOLDER_PLAN = PlanTier.FREE
private val PLACEHOLDER_SUBSCRIPTION: SubscriptionState = SubscriptionState.None

/**
 * Builds the [AccountUiState] from the current Firebase user. Falls back to sensible neutral
 * values when a field is missing (email/password sign-in has no display name, for instance) so
 * the cards always render something coherent rather than a blank.
 */
fun currentAccountUiState(): AccountUiState {
    val user = FirebaseAuth.getInstance().currentUser
    val email = user?.email?.takeIf { it.isNotBlank() } ?: "—"
    val name = user?.displayName?.takeIf { it.isNotBlank() }
        ?: email.substringBefore('@').takeIf { it.isNotBlank() && it != "—" }
        ?: "Account"
    return AccountUiState(
        displayName = name,
        email = email,
        initials = initialsOf(name),
        emailVerified = user?.isEmailVerified == true,
        plan = PLACEHOLDER_PLAN,
        subscription = PLACEHOLDER_SUBSCRIPTION,
        payments = emptyList(),
    )
}

/** Up to two uppercase initials from a display name (or the first letter of a single word). */
private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
