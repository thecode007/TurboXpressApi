package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.thecode007.turboxpress.entity.RestaurantItem

data class RestaurantItemResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val price: Double,
    val category: String?,
    @get:JsonProperty("isAvailable")
    val isAvailable: Boolean,
    val photoUrls: List<String>
) {
    companion object {
        fun from(item: RestaurantItem): RestaurantItemResponse {
            return RestaurantItemResponse(
                id = item.id,
                title = item.title,
                description = item.description,
                price = item.price,
                category = item.category,
                isAvailable = item.isAvailable,
                photoUrls = item.photoUrls
            )
        }
    }
}
