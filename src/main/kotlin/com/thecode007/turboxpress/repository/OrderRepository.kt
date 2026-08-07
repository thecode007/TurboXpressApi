package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.Order
import com.thecode007.turboxpress.entity.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    fun findByRestaurantIdOrderByCreatedAtDesc(restaurantId: Long): List<Order>
    fun findByDriverUserPhoneNumberOrderByCreatedAtDesc(phoneNumber: String): List<Order>
    fun findByCreatedAtAfterOrderByCreatedAtDesc(date: java.time.Instant): List<Order>
    fun findByDriverIsNullAndStatusInOrderByCreatedAtAsc(statuses: List<OrderStatus>): List<Order>
    fun findByDriverIdAndStatusInOrderByCreatedAtDesc(driverId: java.util.UUID, statuses: List<OrderStatus>): List<Order>
    fun findByDriverIdOrderByCreatedAtDesc(driverId: java.util.UUID): List<Order>

    // ── Driver work page (iterator pattern) ──────────────────────────────────

    /**
     * Fetch all DELIVERED orders for a driver within a UTC time window.
     * Used to load a single day's work page.
     */
    fun findByDriverIdAndStatusAndDeliveredAtBetweenOrderByDeliveredAtDesc(
        driverId: java.util.UUID,
        status: OrderStatus,
        start: java.time.Instant,
        end: java.time.Instant
    ): List<Order>

    /**
     * Returns true when at least one DELIVERED order exists before [before].
     * Used to compute [DailyWorkPage.hasPrevious] without loading extra rows.
     */
    fun existsByDriverIdAndStatusAndDeliveredAtBefore(
        driverId: java.util.UUID,
        status: OrderStatus,
        before: java.time.Instant
    ): Boolean

    /**
     * Returns true when at least one DELIVERED order exists from [from] onwards.
     * Used to compute [DailyWorkPage.hasNext] without loading extra rows.
     */
    fun existsByDriverIdAndStatusAndDeliveredAtGreaterThanEqual(
        driverId: java.util.UUID,
        status: OrderStatus,
        from: java.time.Instant
    ): Boolean

    /**
     * Returns all non-delivered orders created in the last 30 days,
     * plus delivered orders whose deliveredAt is within the last 24 hours.
     * This keeps the payload small for the desktop orders screen.
     */
    @Query("""
        SELECT o FROM Order o
        WHERE (
            o.status NOT IN ('DELIVERED', 'CANCELLED', 'REJECTED')
            AND o.createdAt >= :thirtyDaysAgo
        ) OR (
            o.status = 'DELIVERED'
            AND o.deliveredAt >= :twentyFourHoursAgo
        ) OR (
            o.status IN ('CANCELLED', 'REJECTED')
            AND o.createdAt >= :twentyFourHoursAgo
        )
        ORDER BY o.createdAt DESC
    """)
    fun findOrdersForDesktopScreen(
        @Param("thirtyDaysAgo") thirtyDaysAgo: java.time.Instant,
        @Param("twentyFourHoursAgo") twentyFourHoursAgo: java.time.Instant
    ): List<Order>
}


