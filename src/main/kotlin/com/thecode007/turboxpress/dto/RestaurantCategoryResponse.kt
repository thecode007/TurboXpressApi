package com.thecode007.turboxpress.dto

import com.thecode007.turboxpress.entity.RestaurantCategory

data class RestaurantCategoryResponse(
    val id: Long,
    val name: String
) {
    companion object {
        fun from(category: RestaurantCategory): RestaurantCategoryResponse {
            return RestaurantCategoryResponse(
                id = category.id,
                name = category.name
            )
        }
    }
}
