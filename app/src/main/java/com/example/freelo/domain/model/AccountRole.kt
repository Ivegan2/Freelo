package com.example.freelo.domain.model

/** Roles that control which Freelo areas an authenticated account can access. */
enum class AccountRole(val firestoreValue: String) {
    CLIENT("client"),
    FREELANCER("freelancer"),
    ADMIN("admin");

    companion object {
        /** Converts a stored role to the app enum, rejecting unknown values safely. */
        fun fromFirestore(value: String?): AccountRole? = entries.firstOrNull { it.firestoreValue == value }
    }
}
