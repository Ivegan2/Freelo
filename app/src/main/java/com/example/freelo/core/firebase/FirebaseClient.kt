package com.example.freelo.core.firebase

import android.content.Context
import android.os.Build
import com.example.freelo.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

/** Creates Firebase SDK clients and points them at local emulators during development. */
object FirebaseClient {
    private const val LOCAL_APP_NAME = "FreeloLocal"

    @Volatile
    private var clients: Clients? = null

    /** Initializes Auth, Firestore, Storage, and Functions for the configured environment. */
    fun get(context: Context): Clients {
        clients?.let { return it }
        return synchronized(this) {
            clients ?: createClients(context.applicationContext).also { clients = it }
        }
    }

    private fun createClients(context: Context): Clients {
        val existingApp = FirebaseApp.getApps(context).firstOrNull { it.name == LOCAL_APP_NAME }
        val app = existingApp ?: FirebaseApp.initializeApp(context, localOptions(), LOCAL_APP_NAME)
        val auth = FirebaseAuth.getInstance(app)
        val firestore = FirebaseFirestore.getInstance(app)
        val storage = FirebaseStorage.getInstance(app)
        val functions = FirebaseFunctions.getInstance(app, "us-central1")

        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            val emulatorHost = emulatorHostForDevice()
            auth.useEmulator(emulatorHost, 9099)
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setHost("$emulatorHost:8080")
                .setSslEnabled(false)
                .setPersistenceEnabled(false)
                .build()
            storage.useEmulator(emulatorHost, 9199)
            functions.useEmulator(emulatorHost, 5001)
        }

        return Clients(auth, firestore, storage, functions)
    }

    /** Creates a separate Auth session for administrator-created accounts without signing out the admin. */
    fun createSecondaryAuth(context: Context): Pair<FirebaseApp, FirebaseAuth> {
        val mainApp = FirebaseApp.getInstance(LOCAL_APP_NAME)
        val app = FirebaseApp.initializeApp(context.applicationContext, mainApp.options, "FreeloAccount-${UUID.randomUUID()}")
            ?: error("No se pudo crear la sesión aislada de Firebase.")
        val auth = FirebaseAuth.getInstance(app)
        if (BuildConfig.USE_FIREBASE_EMULATOR) auth.useEmulator(emulatorHostForDevice(), 9099)
        return app to auth
    }
    /** Uses Android's host alias in the virtual device and USB reverse on a physical phone. */
    fun emulatorHostForDevice(): String {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        val isVirtualDevice = fingerprint.startsWith("generic")
            || fingerprint.contains("emulator")
            || model.contains("emulator")
            || model.contains("android sdk built for")
            || product.contains("sdk_gphone")
        return if (isVirtualDevice) "10.0.2.2" else "127.0.0.1"
    }

    private fun localOptions(): FirebaseOptions = FirebaseOptions.Builder()
        .setApplicationId("1:1234567890:android:freelo-local")
        .setApiKey("emulator-api-key")
        .setProjectId("demo-freelo")
        .setStorageBucket("demo-freelo.appspot.com")
        .build()

    /** Holds SDK clients so repositories share one configured Firebase app. */
    data class Clients(
        val auth: FirebaseAuth,
        val firestore: FirebaseFirestore,
        val storage: FirebaseStorage,
        val functions: FirebaseFunctions
    )
}
