package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.RestaurantCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface RestaurantCategoryRepository : JpaRepository<RestaurantCategory, Long> {
    fun findByRestaurantIdAndNameIgnoreCase(restaurantId: Long, name: String): Optional<RestaurantCategory>
}
