package com.thecode007.turboxpress.dto

data class RoutePointDto(
    val lat: Double,
    val lng: Double
)

data class RouteResponseDto(
    val distanceKm: Double,
    val timeMs: Long,
    val points: List<RoutePointDto>
)
