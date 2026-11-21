package com.example.freelo.domain.model

/** Public freelance service stored in Firestore's services collection. */
data class ServiceListing(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val title: String,
    val description: String,
    val category: String,
    val price: Long,
    val deliveryDays: Int,
    val availability: String,
    val status: String,
    val imageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Converts this service into fields accepted by Firestore security rules. */
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "ownerId" to ownerId,
        "ownerName" to ownerName,
        "title" to title,
        "description" to description,
        "categoryId" to category,
        "price" to price,
        "deliveryDays" to deliveryDays,
        "availability" to availability,
        "status" to status,
        "imageUrls" to listOfNotNull(imageUrl.takeIf { it.isNotBlank() }),
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        /** Parses a Firestore service document while tolerating optional display fields. */
        fun fromDocument(id: String, data: Map<String, Any?>?): ServiceListing? {
            if (data == null) return null
            val ownerId = data["ownerId"] as? String ?: return null
            return ServiceListing(
                id = id,
                ownerId = ownerId,
                ownerName = data["ownerName"] as? String ?: "Freelancer",
                title = data["title"] as? String ?: return null,
                description = data["description"] as? String ?: "",
                category = data["categoryId"] as? String ?: "General",
                price = (data["price"] as? Number)?.toLong() ?: 0L,
                deliveryDays = (data["deliveryDays"] as? Number)?.toInt() ?: 1,
                availability = data["availability"] as? String ?: "Disponible",
                status = data["status"] as? String ?: "draft",
                imageUrl = (data["imageUrls"] as? List<*>)?.firstOrNull() as? String ?: "",
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/** Item snapshot persisted under carts/{uid}/items/{serviceId}. */
data class CartItem(
    val serviceId: String,
    val ownerId: String,
    val ownerName: String,
    val title: String,
    val price: Long,
    val imageUrl: String = ""
) {
    /** Serializes the cart item for Firestore. */
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "serviceId" to serviceId,
        "ownerId" to ownerId,
        "ownerName" to ownerName,
        "title" to title,
        "price" to price,
        "imageUrl" to imageUrl,
        "updatedAt" to System.currentTimeMillis()
    )

    companion object {
        /** Parses a cart item document. */
        fun fromDocument(id: String, data: Map<String, Any?>?): CartItem? {
            if (data == null) return null
            return CartItem(
                serviceId = data["serviceId"] as? String ?: id,
                ownerId = data["ownerId"] as? String ?: return null,
                ownerName = data["ownerName"] as? String ?: "Freelancer",
                title = data["title"] as? String ?: return null,
                price = (data["price"] as? Number)?.toLong() ?: 0L,
                imageUrl = data["imageUrl"] as? String ?: ""
            )
        }
    }
}
