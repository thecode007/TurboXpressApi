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
    @Query("""
        SELECT o FROM Order o
        WHERE o.driver.userId = :driverId
          AND o.status = :status
          AND COALESCE(o.deliveredAt, o.createdAt) >= :start
          AND COALESCE(o.deliveredAt, o.createdAt) < :end
        ORDER BY COALESCE(o.deliveredAt, o.createdAt) DESC
    """)
    fun findByDriverIdAndStatusAndDeliveredAtBetweenOrderByDeliveredAtDesc(
        @Param("driverId") driverId: java.util.UUID,
        @Param("status") status: OrderStatus,
        @Param("start") start: java.time.Instant,
        @Param("end") end: java.time.Instant
    ): List<Order>

    /**
     * Returns true when at least one DELIVERED order exists before [before].
     * Used to compute [DailyWorkPage.hasPrevious] without loading extra rows.
     */
    @Query("""
        SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
        FROM Order o
        WHERE o.driver.userId = :driverId
          AND o.status = :status
          AND COALESCE(o.deliveredAt, o.createdAt) < :before
    """)
    fun existsByDriverIdAndStatusAndDeliveredAtBefore(
        @Param("driverId") driverId: java.util.UUID,
        @Param("status") status: OrderStatus,
        @Param("before") before: java.time.Instant
    ): Boolean

    /**
     * Returns true when at least one DELIVERED order exists from [from] onwards.
     * Used to compute [DailyWorkPage.hasNext] without loading extra rows.
     */
    @Query("""
        SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
        FROM Order o
        WHERE o.driver.userId = :driverId
          AND o.status = :status
          AND COALESCE(o.deliveredAt, o.createdAt) >= :from
    """)
    fun existsByDriverIdAndStatusAndDeliveredAtGreaterThanEqual(
        @Param("driverId") driverId: java.util.UUID,
        @Param("status") status: OrderStatus,
        @Param("from") from: java.time.Instant
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
            AND COALESCE(o.deliveredAt, o.createdAt) >= :twentyFourHoursAgo
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

    // ── Analytics (Merchant Dashboard) ───────────────────────────────────────
    fun findByRestaurantIdAndCreatedAtAfterOrderByCreatedAtAsc(restaurantId: Long, after: java.time.Instant): List<Order>
    fun findByRestaurantIdAndAcceptedAtIsNotNullAndReadyAtIsNotNullAndCreatedAtAfterOrderByCreatedAtAsc(restaurantId: Long, after: java.time.Instant): List<Order>

    // ── Analytics (Admin Driver Deliveries) ──────────────────────────────────
    fun findByStatusAndDeliveredAtAfterOrderByDeliveredAtDesc(status: OrderStatus, after: java.time.Instant): List<Order>

    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.driver d
        JOIN FETCH d.user
        WHERE (o.status = com.thecode007.turboxpress.entity.OrderStatus.DELIVERED
               OR o.deliveryStatus = com.thecode007.turboxpress.entity.DeliveryStatus.DELIVERED)
          AND COALESCE(o.deliveredAt, o.createdAt) >= :after
        ORDER BY COALESCE(o.deliveredAt, o.createdAt) DESC
    """)
    fun findDeliveredOrdersWithDriverAfter(@Param("after") after: java.time.Instant): List<Order>
}


