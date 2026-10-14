package com.example.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.example.data.model.MarketplaceContactNumber
import com.example.data.model.MarketplacePrice
import com.example.data.model.MarketplaceTextLimits
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MarketplaceRepository(
    private val contentResolver: ContentResolver,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    suspend fun publishListing(
        title: String,
        price: Double,
        seller: String,
        contact: String,
        blockLotPhase: String,
        description: String,
        imageUris: List<Uri>
    ): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in before publishing a marketplace listing.")
        require(imageUris.size <= MAX_IMAGES) { "Choose no more than $MAX_IMAGES images." }
        val (cleanTitle, cleanDescription) = MarketplaceTextLimits.validate(title, description)
        require(price.isFinite() && price > 0.0 && price <= MAX_PRICE) { "Enter a valid listing price." }
        val cleanContact = contact.trim()
        require(MarketplaceContactNumber.isValidStoredNumber(cleanContact)) {
            "Enter a 10-digit Philippine mobile number."
        }
        val cleanBlockLotPhase = blockLotPhase.trim()
        require(cleanBlockLotPhase.length <= MarketplaceTextLimits.BLOCK_LOCATION) {
            "Block / Lot / Phase must be " + MarketplaceTextLimits.BLOCK_LOCATION + " characters or fewer."
        }
        val cleanSeller = seller.trim().ifBlank { "Resident" }.take(MAX_SELLER_LENGTH)

        val listing = firestore.collection("marketplace_items").document()
        val uploadedRefs = mutableListOf<com.google.firebase.storage.StorageReference>()
        try {
            val imagePaths = uploadImages(uid, listing.id, imageUris, uploadedRefs)

            try {
                listing.set(
                    mapOf(
                        "title" to cleanTitle,
                        "price" to price,
                        "seller" to cleanSeller,
                        "contact" to cleanContact,
                        "blockLotPhase" to cleanBlockLotPhase,
                        "description" to cleanDescription,
                        "imageUrls" to emptyList<String>(),
                        "imagePaths" to imagePaths,
                        "sellerUid" to uid,
                        "availabilityStatus" to "AVAILABLE",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Listing database save failed (${(error as? FirebaseFirestoreException)?.code ?: "firestore"}): " +
                        (error.localizedMessage ?: "Could not save listing."),
                    error
                )
            }
            return listing.id
        } catch (error: Exception) {
            uploadedRefs.forEach { ref -> deleteUploadSafely(ref, "roll back") }
            throw error
        }
    }

    suspend fun updateListing(
        listingId: String,
        title: String,
        price: Double,
        contact: String,
        blockLotPhase: String,
        description: String,
        replacementImageUris: List<Uri>
    ) {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in before editing a marketplace listing.")
        require(replacementImageUris.size <= MAX_IMAGES) { "Choose no more than $MAX_IMAGES images." }
        val requestedTitle = title.trim()
        val requestedDescription = description.trim()
        require(price.isFinite() && price > 0.0 && price <= MAX_PRICE) { "Enter a valid listing price." }
        val cleanBlockLotPhase = blockLotPhase.trim()

        val listing = firestore.collection("marketplace_items").document(listingId)
        val current = listing.get().await()
        if (!current.exists() || current.getString("sellerUid") != uid) {
            throw SecurityException("You can only edit your own marketplace listings.")
        }
        val cleanTitle = if (requestedTitle == current.getString("title")) {
            requestedTitle
        } else {
            MarketplaceTextLimits.validateTitle(title)
        }
        val cleanDescription = if (requestedDescription == current.getString("description")) {
            requestedDescription
        } else {
            MarketplaceTextLimits.validateDescription(description)
        }
        if (cleanBlockLotPhase != current.getString("blockLotPhase").orEmpty()) {
            require(cleanBlockLotPhase.length <= MarketplaceTextLimits.BLOCK_LOCATION) {
                "Block / Lot / Phase must be " + MarketplaceTextLimits.BLOCK_LOCATION + " characters or fewer."
            }
        }
        val cleanContact = contact.trim()
        val previousContact = current.getString("contact").orEmpty()
        if (cleanContact != previousContact) {
            require(MarketplaceContactNumber.isValidStoredNumber(cleanContact)) {
                "Enter a 10-digit Philippine mobile number."
            }
        }

        val uploadedRefs = mutableListOf<com.google.firebase.storage.StorageReference>()
        var replacedImagePaths: List<String> = emptyList()
        var uploadedImagePaths: List<String>? = null
        try {
            val update = mutableMapOf<String, Any>(
                "title" to cleanTitle,
                "contact" to cleanContact,
                "blockLotPhase" to cleanBlockLotPhase,
                "description" to cleanDescription,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val existingPrice = current.get("price")
            update["price"] = if (existingPrice is String) {
                val previousAmount = MarketplacePrice.parseStored(existingPrice)
                if (previousAmount != null && previousAmount.compareTo(price.toBigDecimal()) == 0) {
                    existingPrice
                } else {
                    price.toString()
                }
            } else {
                price
            }

            if (replacementImageUris.isNotEmpty()) {
                val replacementPaths = uploadImages(uid, listingId, replacementImageUris, uploadedRefs, versioned = true)
                replacedImagePaths = (current.get("imagePaths") as? List<*>)
                    ?.filterIsInstance<String>()
                    .orEmpty()
                uploadedImagePaths = replacementPaths
                update["imagePaths"] = replacementPaths
                update["imageUrls"] = emptyList<String>()
            }

            listing.update(update).await()
        } catch (error: Exception) {
            uploadedRefs.forEach { ref -> deleteUploadSafely(ref, "roll back") }
            throw error
        }

        val savedImagePaths = uploadedImagePaths
        if (savedImagePaths != null) {
            replacedImagePaths
                .filterNot { it in savedImagePaths }
                .filter { MarketplaceStoragePath.isOwnedImagePath(it, uid, listingId) }
                .forEach { oldPath ->
                    deleteUploadSafely(MarketplaceStorageReference.child(storage, oldPath), "remove replaced")
                }
        }
    }

    suspend fun updateAvailability(listingId: String, availabilityStatus: String): Boolean {
        require(availabilityStatus == "AVAILABLE" || availabilityStatus == "SOLD")
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in before changing listing availability.")
        val listing = firestore.collection("marketplace_items").document(listingId)
        val current = listing.get().await()
        val ownerMatches = current.getString("sellerUid") == uid
        if (!current.exists() || !ownerMatches) {
            throw SecurityException("You can only change your own marketplace listings.")
        }
        if ((current.getString("availabilityStatus") ?: "AVAILABLE") == availabilityStatus) return false
        try {
            listing.update(
                mapOf(
                    "availabilityStatus" to availabilityStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (error: Exception) {
            throw IllegalStateException(
                "Availability database update failed (${(error as? FirebaseFirestoreException)?.code ?: "firestore"}): " +
                    (error.localizedMessage ?: "Could not update listing status."),
                error
            )
        }
        return true
    }

    private suspend fun uploadImages(
        uid: String,
        listingId: String,
        uris: List<Uri>,
        uploadedRefs: MutableList<com.google.firebase.storage.StorageReference>,
        versioned: Boolean = false
    ): List<String> = uris.mapIndexed { index, uri ->
        val mimeType = contentResolver.getType(uri)
            ?.takeIf { it.startsWith("image/") }
            ?: throw IllegalArgumentException("One of the selected files is not an image.")
        val version = if (versioned) UUID.randomUUID().toString().replace("-", "") else null
        val objectPath = MarketplaceStoragePath.imagePath(uid, listingId, index, version)
        val imageRef = MarketplaceStorageReference.child(storage, objectPath)
        val metadata = StorageMetadata.Builder().setContentType(mimeType).build()
        var stage = "upload"
        try {
            // Use snapshot metadata directly to avoid -13010 object not found indexing delays
            val snapshot = imageRef.putFile(uri, metadata).await()
            uploadedRefs += imageRef
            stage = "post-upload metadata verification"
            val uploaded = snapshot.metadata
            check(uploaded != null && uploaded.sizeBytes > 0L && uploaded.contentType?.startsWith("image/") == true) {
                "Uploaded image could not be verified in Firebase Storage."
            }
        } catch (error: Exception) {
            val storageError = error.findStorageException()
            Log.e(
                STORAGE_LOG_TAG,
                "Marketplace image " + index + " failed during " + stage +
                    "; bucket=" + imageRef.bucket + ", object=" + imageRef.name +
                    ", storageErrorCode=" + (storageError?.errorCode ?: "unknown") +
                    ", httpResultCode=" + (storageError?.httpResultCode ?: "unknown") + ".",
                error
            )
            val detail = when (storageError?.errorCode) {
                StorageException.ERROR_OBJECT_NOT_FOUND ->
                    "Storage returned HTTP 404 for the upload target. Verify that the configured bucket exists and the object path is valid."
                StorageException.ERROR_BUCKET_NOT_FOUND ->
                    "The configured Firebase Storage bucket does not exist. Check the Firebase app configuration and project bucket."
                else -> error.localizedMessage ?: "Could not verify the uploaded image."
            }
            val httpResult = storageError?.httpResultCode?.takeIf { it > 0 }
                ?.let { ", HTTP " + it }
                .orEmpty()
            throw IllegalStateException(
                "Marketplace image " + index + " " + stage + " failed (Storage " +
                    (storageError?.errorCode ?: "unknown") + httpResult + ", bucket=" +
                    imageRef.bucket + "): " + detail,
                error
            )
        }
        imageRef.path
    }

    private suspend fun deleteUploadSafely(
        ref: com.google.firebase.storage.StorageReference,
        operation: String
    ) {
        try {
            ref.delete().await()
        } catch (error: Exception) {
            Log.w(
                STORAGE_LOG_TAG,
                "Could not " + operation + " a marketplace object in bucket=" +
                    ref.bucket + ", object=" + ref.name + ".",
                error
            )
        }
    }

    private fun Throwable.findStorageException(): StorageException? {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is StorageException) return cause
            cause = cause.cause
        }
        return null
    }

    companion object {
        const val MAX_IMAGES = 3
        private const val STATUS_LOG_TAG = "MarketplaceStatus"
        private const val STORAGE_LOG_TAG = "MarketplaceStorage"
        private const val MAX_PRICE = 999999999.99
        private const val MAX_SELLER_LENGTH = 100
    }
}

internal object MarketplaceStoragePath {
    private val versionPattern = Regex("[A-Za-z0-9]{32}")
    private val supportedImagePathPattern = Regex(
        "^marketplace/([^/]+)/([^/]+)/image_[0-2](_[A-Za-z0-9]{32})?$"
    )

    fun imagePath(uid: String, listingId: String, index: Int, version: String? = null): String {
        require(uid.isNotBlank()) { "A signed-in user is required to upload listing images." }
        require(listingId.isNotBlank()) { "A listing ID is required to upload listing images." }
        require(index in 0 until MarketplaceRepository.MAX_IMAGES) { "Invalid marketplace image index." }
        require(version == null || versionPattern.matches(version)) { "Invalid marketplace image version." }
        val objectName = if (version == null) "image_$index" else "image_${index}_$version"
        return "marketplace/$uid/$listingId/$objectName"
    }

    fun isOwnedImagePath(path: String, uid: String, listingId: String): Boolean =
        supportedImagePathPattern.matches(path) &&
            path.startsWith("marketplace/$uid/$listingId/")
}

internal object MarketplaceStorageReference {
    fun child(
        storage: FirebaseStorage,
        imagePath: String
    ): com.google.firebase.storage.StorageReference = storage.reference.child(imagePath)
}
