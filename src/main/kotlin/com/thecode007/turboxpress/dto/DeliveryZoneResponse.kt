package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.thecode007.turboxpress.entity.DeliveryZone

data class LatLng(
    val lat: Double,
    val lng: Double
)

data class DeliveryZoneResponse(
    val id: Long,
    val name: String,
    val centerLat: Double,
    val centerLng: Double,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val polygonPoints: List<LatLng>
) {
    companion object {
        fun from(zone: DeliveryZone): DeliveryZoneResponse {
            val coordinates = zone.polygon.coordinates.map { coord ->
                LatLng(lat = coord.y, lng = coord.x)
            }
            val centroid = zone.polygon.centroid
            return DeliveryZoneResponse(
                id = zone.id,
                name = zone.name,
                centerLat = centroid.y,
                centerLng = centroid.x,
                isActive = zone.isActive,
                polygonPoints = coordinates
            )
        }
    }
}
