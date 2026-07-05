package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.Order
import com.thecode007.turboxpress.entity.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    fun findByRestaurantIdOrderByCreatedAtDesc(restaurantId: Long): List<Order>
    fun findByDriverUserPhoneNumberOrderByCreatedAtDesc(phoneNumber: String): List<Order>
    fun findByCreatedAtAfterOrderByCreatedAtDesc(date: java.time.Instant): List<Order>
    fun findByDriverIsNullAndStatusInOrderByCreatedAtAsc(statuses: List<OrderStatus>): List<Order>
    fun findByDriverIdAndStatusInOrderByCreatedAtDesc(driverId: java.util.UUID, statuses: List<OrderStatus>): List<Order>
    fun findByDriverIdOrderByCreatedAtDesc(driverId: java.util.UUID): List<Order>
}
