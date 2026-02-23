package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.RestaurantItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RestaurantItemRepository : JpaRepository<RestaurantItem, Long>
