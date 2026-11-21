package com.example.freelo.domain.model

/** Profile fields stored in the Firestore users/{uid} document. */
data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val role: AccountRole,
    val active: Boolean = true,
    val phone: String? = null,
    val photoUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val specialties: List<String> = emptyList(),
    val isPublic: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Serializes a profile using stable Firestore field names. */
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "name" to name,
        "email" to email,
        "role" to role.firestoreValue,
        "active" to active,
        "phone" to phone,
        "photoUrl" to photoUrl,
        "bio" to bio,
        "location" to location,
        "specialties" to specialties,
        "isPublic" to isPublic,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        /** Builds a profile from Firestore data and returns null for malformed records. */
        fun fromFirestoreMap(data: Map<String, Any?>?): UserProfile? {
            if (data == null) return null
            val uid = data["uid"] as? String ?: return null
            val name = data["name"] as? String ?: return null
            val email = data["email"] as? String ?: return null
            val role = AccountRole.fromFirestore(data["role"] as? String) ?: return null
            return UserProfile(
                uid = uid,
                name = name,
                email = email,
                role = role,
                active = data["active"] as? Boolean ?: true,
                phone = data["phone"] as? String,
                photoUrl = data["photoUrl"] as? String,
                bio = data["bio"] as? String,
                location = data["location"] as? String,
                specialties = (data["specialties"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                isPublic = data["isPublic"] as? Boolean ?: false,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}
