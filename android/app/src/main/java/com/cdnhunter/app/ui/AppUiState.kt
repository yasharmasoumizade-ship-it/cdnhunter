package com.cdnhunter.app.ui

import com.google.firebase.auth.FirebaseAuth

/**
 * Single source of truth for the account / subscription strings that Settings' [AccountCard]
 * and [ProfileScreen] both display.
 *
 * Before this holder those two screens each hard-coded their own copy of the name, email and
 * plan ("Yashar M." / "yashar@ananasvpn.com" / "Expires in 21 days" in one place, the same
 * facts spelled differently in the other), so they could — and did — disagree. Both now read
 * one [AccountUiState], derived once from [FirebaseAuth] and passed down by parameter, so the
 * identity shown is the actually-signed-in user and the two screens can never drift apart.
 *
 * The subscription fields are still placeholder values (the app has no plan/billing backend
 * yet), but they live here once instead of being duplicated per screen — wiring them to a real
 * entitlement source is a later, single-site change.
 */
data class AccountUiState(
    val displayName: String,
    val email: String,
    val initials: String,
    val planName: String,
    val daysRemaining: Int,
    val daysTotal: Int,
) {
    /** "Expires in 21 days" — the short form shown on Settings' account row. */
    val expiresLabel: String
        get() = "Expires in $daysRemaining days"

    /** "21 of 30 days remaining" — the long form shown on Profile's plan card. */
    val daysRemainingLabel: String
        get() = "$daysRemaining of $daysTotal days remaining"

    /** Progress of the current billing period, 0f..1f, for Profile's plan meter. */
    val periodProgress: Float
        get() = if (daysTotal <= 0) 0f else (daysRemaining.toFloat() / daysTotal).coerceIn(0f, 1f)
}

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
        // Placeholder subscription facts — one definition, both screens read it.
        planName = "Pro plan",
        daysRemaining = 21,
        daysTotal = 30,
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
