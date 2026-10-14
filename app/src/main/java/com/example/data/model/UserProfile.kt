package com.example.data.model

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val providerIds: List<String> = emptyList(),
    val emailVerified: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val lastLoginAt: Timestamp? = null,
    val termsAcceptedAt: Timestamp? = null,
    val privacyPolicyAcceptedAt: Timestamp? = null,
    val accountStatus: String = "active",
    val contact: String = "",
    val block: String = "",
    val lot: String = "",
    val phase: String = "",
    val role: String = "Visitor",
    val isAdmin: Boolean = false,
    val roleRequestStatus: String = "none",
    val roleVerified: Boolean = false,
    val requestedRole: String = "",
    val proofDocumentPath: String = "",
    val photoStoragePath: String = "",
    val proofDocumentUrl: String = "",
    val notifyMarketplace: Boolean = true,
    val notifyCommunityChat: Boolean = true,
    val notifyConcernsRequests: Boolean = true,
    val notifyAnnouncements: Boolean = false,
    val notifyAmenityReservations: Boolean = false,
    val notifyCommunityEvents: Boolean = false,
    val notifyEmergencyContactUpdates: Boolean = false
)

data class UsernameReservation(
    val username: String = "",
    val uid: String = "",
    val createdAt: Timestamp? = null
)
