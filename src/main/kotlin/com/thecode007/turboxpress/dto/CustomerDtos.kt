package com.thecode007.turboxpress.dto

data class CustomerSearchResponse(
    val id: Long,
    val userId: java.util.UUID?,
    val fullName: String,
    val phoneNumber: String,
    val deliveryZoneId: Long?,
    val deliveryZoneName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val detailedAddress: String?
)

data class UpdateCustomerRequest(
    val fullName: String? = null,
    val deliveryZoneId: Long? = null,
    /** Pass true to clear the exact pinned coordinates (reset to zone-only mode). */
    val resetCoordinates: Boolean = false,
    val detailedAddress: String? = null
)
