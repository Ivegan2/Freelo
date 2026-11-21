package com.example.freelo.data.auth

import com.example.freelo.core.firebase.FirebaseClient
import com.example.freelo.domain.model.AccountRole
import com.example.freelo.domain.model.UserProfile
import android.content.Context
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.tasks.await

/** Implements email/password account flows and stores the app profile in Firestore. */
class AuthRepository(private val clients: FirebaseClient.Clients) {
    /** Registers a user account (Client, Freelancer, or Admin) and stores their profile in Firestore. */
    suspend fun register(name: String, email: String, password: String, role: AccountRole): UserProfile {
        val normalizedEmail = email.trim().lowercase()
        require(name.isNotBlank()) { "Escribe tu nombre." }
        require(normalizedEmail.contains('@')) { "Escribe un correo válido." }
        require(password.length >= 8) { "La contraseña debe tener al menos 8 caracteres." }

        val authUser = try {
            clients.auth.createUserWithEmailAndPassword(normalizedEmail, password).await().user
        } catch (error: FirebaseAuthUserCollisionException) {
            throw IllegalArgumentException("Ya existe una cuenta con ese correo.", error)
        } ?: error("Firebase no devolvió el usuario creado.")

        val profile = UserProfile(
            uid = authUser.uid,
            name = name.trim(),
            email = normalizedEmail,
            role = role
        )

        try {
            clients.firestore.collection("users").document(authUser.uid)
                .set(profile.toFirestoreMap()).await()
            return profile
        } catch (error: Exception) {
            // Prevent an Auth account with no corresponding role profile.
            runCatching { authUser.delete().await() }
            throw error
        }
    }

    /** Creates an account through an isolated Auth session so the administrator remains signed in. */
    suspend fun createManagedUser(context: Context, name: String, email: String, password: String, role: AccountRole): UserProfile {
        require(role != AccountRole.ADMIN) { "No se permite crear otro administrador desde este formulario." }
        val normalizedEmail = email.trim().lowercase()
        require(name.isNotBlank()) { "Escribe el nombre del usuario." }
        require(normalizedEmail.contains('@')) { "Escribe un correo válido." }
        require(password.length >= 8) { "La contraseña debe tener al menos 8 caracteres." }
        val (app, isolatedAuth) = FirebaseClient.createSecondaryAuth(context)
        try {
            val user = isolatedAuth.createUserWithEmailAndPassword(normalizedEmail, password).await().user
                ?: error("Firebase no devolvió el usuario creado.")
            val profile = UserProfile(user.uid, name.trim(), normalizedEmail, role)
            try {
                clients.firestore.collection("users").document(user.uid).set(profile.toFirestoreMap()).await()
                return profile
            } catch (error: Exception) {
                runCatching { user.delete().await() }
                throw error
            }
        } finally {
            runCatching { isolatedAuth.signOut() }
            app.delete()
        }
    }

    /** Signs in and loads the role and active state from the protected Firestore profile. */
    suspend fun signIn(email: String, password: String): UserProfile {
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.contains('@')) { "Escribe un correo válido." }
        require(password.isNotBlank()) { "Escribe tu contraseña." }

        try {
            val user = clients.auth.signInWithEmailAndPassword(normalizedEmail, password).await().user
                ?: error("Firebase no devolvió el usuario autenticado.")
            val snapshot = clients.firestore.collection("users").document(user.uid).get().await()
            val profile = UserProfile.fromFirestoreMap(snapshot.data)
            if (profile == null || !profile.active) {
                clients.auth.signOut()
                throw IllegalStateException("La cuenta no está activa o no tiene un perfil válido.")
            }
            return profile
        } catch (error: FirebaseAuthInvalidCredentialsException) {
            throw IllegalArgumentException("Correo o contraseña incorrectos.", error)
        }
    }

    /** Restores the signed-in profile after a local biometric check. */
    suspend fun restoreCurrentProfile(): UserProfile {
        val user = clients.auth.currentUser ?: throw IllegalStateException("Inicia sesión primero con correo y contraseña.")
        val snapshot = clients.firestore.collection("users").document(user.uid).get().await()
        val profile = UserProfile.fromFirestoreMap(snapshot.data)
        if (profile == null || !profile.active) {
            clients.auth.signOut()
            throw IllegalStateException("La cuenta no está activa o no tiene un perfil válido.")
        }
        return profile
    }

    /** Sends Firebase's password-reset email to an existing account. */
    suspend fun sendPasswordReset(email: String) {
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.contains('@')) { "Escribe un correo válido." }
        clients.auth.sendPasswordResetEmail(normalizedEmail).await()
    }

    /** Clears the Firebase Auth session on this device. */
    fun signOut() = clients.auth.signOut()

    /** Returns the current Auth user, if a session has already been restored. */
    fun currentUid(): String? = clients.auth.currentUser?.uid
}
