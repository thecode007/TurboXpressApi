package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    fun findByRestaurantIdOrderByCreatedAtDesc(restaurantId: Long): List<Order>
    fun findByDriverPhoneNumberOrderByCreatedAtDesc(phoneNumber: String): List<Order>
    fun findByCreatedAtAfterOrderByCreatedAtDesc(date: java.time.Instant): List<Order>
}
