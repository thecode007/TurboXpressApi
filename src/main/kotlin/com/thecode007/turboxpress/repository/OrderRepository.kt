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
    fun findByDriverIdAndStatusInOrderByCreatedAtAsc(driverId: java.util.UUID, statuses: List<OrderStatus>): List<Order>
    fun findByDriverIdAndStatusInOrderByCreatedAtDesc(driverId: java.util.UUID, statuses: List<OrderStatus>): List<Order>
    fun countByDriverIdAndStatusIn(driverId: java.util.UUID, statuses: List<OrderStatus>): Long
    fun findByDriverIdOrderByCreatedAtDesc(driverId: java.util.UUID): List<Order>

    @Query("""
        SELECT o.driver.userId, COUNT(o)
        FROM Order o
        WHERE o.driver.userId IN :driverIds
          AND o.status IN :statuses
        GROUP BY o.driver.userId
    """)
    fun countActiveOrdersByDriverIds(
        @Param("driverIds") driverIds: Collection<java.util.UUID>,
        @Param("statuses") statuses: Collection<OrderStatus>
    ): List<Array<Any>>

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
     * Returns all non-delivered orders created since [activeSince] (typically last 24h),
     * plus delivered orders whose deliveredAt is within the last 24 hours.
     * Eagerly joins customer, zone, restaurant, and driver to eliminate N+1 queries.
     */
    @Query("""
        SELECT DISTINCT o FROM Order o
        LEFT JOIN FETCH o.customer c
        LEFT JOIN FETCH c.deliveryZone
        LEFT JOIN FETCH o.restaurant
        LEFT JOIN FETCH o.driver d
        LEFT JOIN FETCH d.user
        WHERE (
            o.status NOT IN ('DELIVERED', 'CANCELLED', 'REJECTED')
            AND o.createdAt >= :activeSince
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
        @Param("activeSince") activeSince: java.time.Instant,
        @Param("twentyFourHoursAgo") twentyFourHoursAgo: java.time.Instant
    ): List<Order>

    /**
     * Archive query: finds all orders (or filtered by customer/status/dates) with no 24-hour limit.
     * Eagerly joins customer, zone, restaurant, and driver to eliminate N+1 queries.
     */
    @Query("""
        SELECT DISTINCT o FROM Order o
        LEFT JOIN FETCH o.customer c
        LEFT JOIN FETCH c.deliveryZone
        LEFT JOIN FETCH o.restaurant r
        LEFT JOIN FETCH o.driver d
        LEFT JOIN FETCH d.user
        WHERE (:customerQuery IS NULL OR (c IS NOT NULL AND (LOWER(c.fullName) LIKE LOWER(CONCAT('%', :customerQuery, '%')) OR c.phoneNumber LIKE CONCAT('%', :customerQuery, '%'))))
          AND (:status IS NULL OR o.status = :status)
          AND (:restaurantId IS NULL OR (r IS NOT NULL AND r.id = :restaurantId))
          AND (:fromInstant IS NULL OR o.createdAt >= :fromInstant)
          AND (:toInstant IS NULL OR o.createdAt <= :toInstant)
        ORDER BY o.createdAt DESC
    """)
    fun findArchivedOrders(
        @Param("customerQuery") customerQuery: String?,
        @Param("status") status: OrderStatus?,
        @Param("restaurantId") restaurantId: Long?,
        @Param("fromInstant") fromInstant: java.time.Instant?,
        @Param("toInstant") toInstant: java.time.Instant?
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


