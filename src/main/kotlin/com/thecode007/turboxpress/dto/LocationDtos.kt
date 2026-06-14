package com.thecode007.turboxpress.dto

import java.util.UUID

data class UpdateLocationRequest(
    val driverId: UUID,
    val latitude: Double,
    val longitude: Double
)

data class NearestDriverResponse(
    val driverId: UUID,
    val driverName: String?,
    val latitude: Double,
    val longitude: Double,
    val vehiclePlate: String?
)
