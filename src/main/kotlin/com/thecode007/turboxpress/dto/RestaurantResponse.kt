package com.thecode007.turboxpress.dto

import com.thecode007.turboxpress.entity.Restaurant

data class RestaurantResponse(
    val id: Long,
    val name: String,
    val logoUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val owner: OwnerResponse,
    val items: List<RestaurantItemResponse> = emptyList()
) {
    companion object {
        fun from(restaurant: Restaurant): RestaurantResponse {
            return RestaurantResponse(
                id = restaurant.id,
                name = restaurant.name,
                logoUrl = restaurant.logoUrl,
                latitude = restaurant.location.y,
                longitude = restaurant.location.x,
                owner = OwnerResponse.from(restaurant.owner),
                items = restaurant.items.map { RestaurantItemResponse.from(it) }
            )
        }
    }
}
