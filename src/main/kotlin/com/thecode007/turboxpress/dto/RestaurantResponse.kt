package com.thecode007.turboxpress.dto

import com.thecode007.turboxpress.entity.Restaurant
import java.math.BigDecimal

data class RestaurantResponse(
    val id: Long,
    val name: String,
    val logoUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val owner: OwnerResponse,
    val monthlySubFee: Double,
    val commissionRate: Double,
    val balance: BigDecimal = BigDecimal.ZERO,
    val isActive: Boolean = true,
    val nextBillingDate: java.time.LocalDate? = null,
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
                monthlySubFee = restaurant.monthlySubFee,
                commissionRate = restaurant.commissionRate,
                balance = restaurant.balance,
                isActive = restaurant.isActive,
                nextBillingDate = restaurant.nextBillingDate,
                items = restaurant.items.map { RestaurantItemResponse.from(it) }
            )
        }
    }
}
