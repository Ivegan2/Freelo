package com.example.freelo.data.marketplace

import android.net.Uri
import com.example.freelo.core.firebase.FirebaseClient
import com.example.freelo.BuildConfig
import com.example.freelo.domain.model.AccountRole
import com.example.freelo.domain.model.CartItem
import com.example.freelo.domain.model.ServiceListing
import com.example.freelo.domain.model.UserProfile
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Coordinates Firestore and Storage operations used by marketplace screens. */
class MarketplaceRepository(private val clients: FirebaseClient.Clients) {
    /** Loads one public or administrative user profile. */
    suspend fun getUser(uid: String): UserProfile? = clients.firestore.collection("users")
        .document(uid).get().await().let { UserProfile.fromFirestoreMap(it.data) }

    /** Lists profiles for the administrator's user-management screen. */
    suspend fun listUsers(): List<UserProfile> = clients.firestore.collection("users")
        .get().await().documents.mapNotNull { UserProfile.fromFirestoreMap(it.data) }

    /** Updates editable account fields without changing authentication credentials. */
    suspend fun updateUser(profile: UserProfile) {
        clients.firestore.collection("users").document(profile.uid)
            .set(profile.copy(updatedAt = System.currentTimeMillis()).toFirestoreMap(), SetOptions.merge()).await()
    }

    /** Requests trusted backend code to remove both the Firebase Auth account and its profile. */
    suspend fun removeUser(uid: String) {
        if (!BuildConfig.USE_FIREBASE_EMULATOR) {
            clients.functions.getHttpsCallable("adminDeleteUser").call(mapOf("uid" to uid)).await()
            return
        }

        // The local demo intentionally uses placeholder Firebase options, so call the local
        // callable endpoint with the signed-in emulator token instead of requesting an FIS token.
        val idToken = try {
            clients.auth.currentUser?.getIdToken(false)?.await()?.token
                ?: error("La sesión del administrador expiró. Vuelve a iniciar sesión.")
        } catch (error: Exception) {
            throw IllegalStateException("No se pudo obtener el token de la sesión para eliminar la cuenta: ${error.message}", error)
        }
        withContext(Dispatchers.IO) {
            val endpoint = URL("http://${FirebaseClient.emulatorHostForDevice()}:5001/demo-freelo/us-central1/adminDeleteUser")
            val connection = endpoint.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $idToken")
                connection.outputStream.use { output ->
                    output.write(JSONObject().put("data", JSONObject().put("uid", uid)).toString().toByteArray(Charsets.UTF_8))
                }
                val responseCode = connection.responseCode
                val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (responseCode !in 200..299) {
                    val message = runCatching { JSONObject(responseText).getJSONObject("error").optString("message") }.getOrNull()
                    error(message?.takeIf { it.isNotBlank() } ?: "No se pudo eliminar la cuenta (HTTP $responseCode).")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Publishes a service for buyers or lists the signed-in freelancer's services. */
    suspend fun listServices(role: AccountRole, uid: String): List<ServiceListing> {
        val query = when (role) {
            AccountRole.ADMIN -> clients.firestore.collection("services")
            AccountRole.FREELANCER -> clients.firestore.collection("services").whereEqualTo("ownerId", uid)
            AccountRole.CLIENT -> clients.firestore.collection("services").whereEqualTo("status", "published")
        }
        return query.get().await().documents.mapNotNull { ServiceListing.fromDocument(it.id, it.data) }
            .sortedByDescending { it.updatedAt }
    }

    /** Saves a new freelancer service as draft or pending moderation. */
    suspend fun saveService(service: ServiceListing): String {
        val ref = if (service.id.isBlank()) clients.firestore.collection("services").document()
        else clients.firestore.collection("services").document(service.id)
        ref.set(service.copy(id = ref.id, updatedAt = System.currentTimeMillis()).toFirestoreMap(), SetOptions.merge()).await()
        return ref.id
    }

    /** Changes a service's moderation state. */
    suspend fun setServiceStatus(service: ServiceListing, status: String) {
        clients.firestore.collection("services").document(service.id).update(
            mapOf("status" to status, "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    /** Updates the service image URL after a successful Storage upload. */
    suspend fun setServiceImage(serviceId: String, imageUrl: String) {
        clients.firestore.collection("services").document(serviceId).update(
            mapOf("imageUrls" to listOf(imageUrl), "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    /** Deletes a service and best-effort deletes its uploaded image. */
    suspend fun deleteService(service: ServiceListing) {
        if (service.imageUrl.isNotBlank()) runCatching { clients.storage.getReferenceFromUrl(service.imageUrl).delete().await() }
        clients.firestore.collection("services").document(service.id).delete().await()
    }

    /** Uploads a selected image and returns its public download URL. */
    suspend fun uploadServiceImage(ownerId: String, serviceId: String, image: Uri): String {
        val ref = clients.storage.reference.child("services/$ownerId/$serviceId/${UUID.randomUUID()}.jpg")
        ref.putFile(image).await()
        return ref.downloadUrl.await().toString()
    }

    /** Uploads a camera preview as JPEG bytes and returns its download URL. */
    suspend fun uploadServiceImageBytes(ownerId: String, serviceId: String, bytes: ByteArray): String {
        val ref = clients.storage.reference.child("services/$ownerId/$serviceId/${UUID.randomUUID()}.jpg")
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        ref.putBytes(bytes, metadata).await()
        return ref.downloadUrl.await().toString()
    }

    /** Adds or replaces a service in the signed-in client's persistent cart. */
    suspend fun addToCart(uid: String, service: ServiceListing) {
        val item = CartItem(service.id, service.ownerId, service.ownerName, service.title, service.price, service.imageUrl)
        clients.firestore.collection("carts").document(uid).collection("items").document(service.id)
            .set(item.toFirestoreMap()).await()
    }

    /** Removes one service from the client's cart. */
    suspend fun removeCartItem(uid: String, serviceId: String) {
        clients.firestore.collection("carts").document(uid).collection("items").document(serviceId).delete().await()
    }

    /** Reads the client's current cart. */
    suspend fun getCart(uid: String): List<CartItem> = clients.firestore.collection("carts").document(uid)
        .collection("items").get().await().documents.mapNotNull { CartItem.fromDocument(it.id, it.data) }

    /** Creates a pending order; it never represents a completed payment. */
    suspend fun createPendingOrder(uid: String, items: List<CartItem>, address: String, latitude: Double?, longitude: Double?): String {
        require(items.isNotEmpty()) { "Agrega un servicio al carrito antes de continuar." }
        require(address.isNotBlank()) { "Escribe la dirección de entrega o comparte tu ubicación." }
        require(items.map { it.ownerId }.distinct().size == 1) { "Realiza un pedido por freelancer para mantener el seguimiento separado." }
        val subtotal = items.sumOf { it.price }
        val commission = subtotal / 10
        val payload = mapOf(
            "clientId" to uid,
            "freelancerId" to items.first().ownerId,
            "items" to items.map { mapOf("serviceId" to it.serviceId, "title" to it.title, "ownerId" to it.ownerId, "price" to it.price) },
            "subtotal" to subtotal,
            "commission" to commission,
            "total" to subtotal + commission,
            "address" to address,
            "latitude" to latitude,
            "longitude" to longitude,
            "status" to "pending",
            "paymentStatus" to "pending",
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis()
        )
        val orderRef = clients.firestore.collection("orders").document()
        orderRef.set(payload).await()
        val batch = clients.firestore.batch()
        items.forEach { item -> batch.delete(clients.firestore.collection("carts").document(uid).collection("items").document(item.serviceId)) }
        batch.commit().await()
        return orderRef.id
    }

    /** Confirms the payment of an order through the certified sandbox payment gateway. */
    suspend fun confirmOrderPayment(
        orderId: String,
        transactionId: String,
        paymentMethod: String,
        authorizationCode: String
    ) {
        clients.firestore.collection("orders").document(orderId).update(
            mapOf(
                "paymentStatus" to "paid",
                "status" to "accepted",
                "transactionId" to transactionId,
                "paymentMethod" to paymentMethod,
                "authorizationCode" to authorizationCode,
                "paidAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    /** Lists orders visible to the signed-in user according to their role. */
    suspend fun listOrders(role: AccountRole, uid: String): List<Map<String, Any?>> {
        val query = when (role) {
            AccountRole.ADMIN -> clients.firestore.collection("orders")
            AccountRole.CLIENT -> clients.firestore.collection("orders").whereEqualTo("clientId", uid)
            AccountRole.FREELANCER -> clients.firestore.collection("orders").whereEqualTo("freelancerId", uid)
        }
        return query.get().await().documents.map { it.data.orEmpty() + ("id" to it.id) }
            .sortedByDescending { (it["createdAt"] as? Number)?.toLong() ?: 0L }
    }

    /** Updates a fulfillment status while leaving payment status untouched. */
    suspend fun updateOrderStatus(orderId: String, status: String) {
        clients.firestore.collection("orders").document(orderId).update(
            mapOf("status" to status, "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    /** Loads one order for its client, freelancer, or administrator. */
    suspend fun getOrder(orderId: String): Map<String, Any?>? = clients.firestore.collection("orders")
        .document(orderId).get().await().let { snapshot -> snapshot.data?.plus("id" to snapshot.id) }

    /** Saves the user's personal profile fields. */
    suspend fun saveProfile(profile: UserProfile) {
        clients.firestore.collection("users").document(profile.uid)
            .set(profile.copy(updatedAt = System.currentTimeMillis()).toFirestoreMap(), SetOptions.merge()).await()
    }

    /** Uploads a profile image and returns its download URL. */
    suspend fun uploadProfileImage(uid: String, image: Uri): String {
        val ref = clients.storage.reference.child("profiles/$uid/${UUID.randomUUID()}.jpg")
        ref.putFile(image).await()
        return ref.downloadUrl.await().toString()
    }

    /** Opens or creates a conversation between the active client and a freelancer. */
    suspend fun openConversation(clientId: String, freelancerId: String): String {
        val id = "${clientId}_$freelancerId"
        clients.firestore.collection("conversations").document(id).set(
            mapOf("members" to listOf(clientId, freelancerId), "clientId" to clientId,
                "freelancerId" to freelancerId, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()
        ).await()
        return id
    }

    /** Lists conversations visible to the active user. */
    suspend fun listConversations(uid: String): List<Map<String, Any?>> = clients.firestore.collection("conversations")
        .whereArrayContains("members", uid).get().await().documents.map { it.data.orEmpty() + ("id" to it.id) }

    /** Loads ordered messages for a conversation. */
    suspend fun listMessages(conversationId: String): List<Map<String, Any?>> = clients.firestore
        .collection("conversations").document(conversationId).collection("messages")
        .orderBy("createdAt").get().await().documents.map { it.data.orEmpty() + ("id" to it.id) }

    /** Sends one message and updates the conversation's recent activity timestamp. */
    suspend fun sendMessage(conversationId: String, senderId: String, text: String) {
        require(text.isNotBlank()) { "Escribe un mensaje." }
        val conversation = clients.firestore.collection("conversations").document(conversationId)
        conversation.collection("messages").add(mapOf(
            "senderId" to senderId, "text" to text.trim(), "createdAt" to System.currentTimeMillis()
        )).await()
        conversation.update("updatedAt", System.currentTimeMillis()).await()
    }

    /** Returns a summary of real paid orders for the administrator. */
    suspend fun salesSummary(): Map<String, Long> {
        val paid = clients.firestore.collection("orders").whereEqualTo("paymentStatus", "paid").get().await().documents
        return mapOf("orders" to paid.size.toLong(), "total" to paid.sumOf { it.getLong("total") ?: 0L },
            "commission" to paid.sumOf { it.getLong("commission") ?: 0L })
    }

    /** Returns live role-specific values for the admin or freelancer dashboard. */
    suspend fun dashboardCounts(role: AccountRole, uid: String): Map<String, Long> {
        if (role == AccountRole.ADMIN) {
            val userCount = clients.firestore.collection("users").get().await().documents.size.toLong()
            val serviceDocs = clients.firestore.collection("services").get().await().documents
            val orders = clients.firestore.collection("orders").get().await().documents
            val commission = orders.filter { it.getString("paymentStatus") == "paid" }
                .sumOf { it.getLong("commission") ?: 0L }
            return mapOf("users" to userCount, "services" to serviceDocs.count { it.getString("status") == "published" }.toLong(), "commission" to commission)
        }
        val services = listServices(role, uid)
        val orders = listOrders(role, uid)
        return mapOf(
            "services" to services.count { it.status == "published" }.toLong(),
            "orders" to orders.count { it["status"] !in listOf("delivered", "cancelled") }.toLong()
        )
    }

    /** Lists moderation and user reports for administrators. */
    suspend fun listReports(): List<Map<String, Any?>> = clients.firestore.collection("reports")
        .get().await().documents.map { it.data.orEmpty() + ("id" to it.id) }
}
