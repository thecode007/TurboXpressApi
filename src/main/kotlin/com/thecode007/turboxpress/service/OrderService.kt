package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.*
import com.thecode007.turboxpress.entity.*
import com.thecode007.turboxpress.exception.ResourceNotFoundException
import com.thecode007.turboxpress.repository.*
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val restaurantRepository: RestaurantRepository,
    private val restaurantItemRepository: RestaurantItemRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
    private val deliveryZoneRepository: DeliveryZoneRepository,
    private val customerRepository: CustomerRepository,
    private val customerProfileRepository: CustomerProfileRepository,
    private val roleRepository: RoleRepository,
    private val appSettingRepository: com.thecode007.turboxpress.repository.AppSettingRepository,
    private val orderEventBroadcaster: OrderEventBroadcaster,
    private val driverLocationService: DriverLocationService,
    private val entityManager: EntityManager,
    @org.springframework.beans.factory.annotation.Value("\${driver.online.stale-threshold-seconds:45}")
    private val staleThresholdSeconds: Long = 45
) {

    companion object {
        private val log = LoggerFactory.getLogger(OrderService::class.java)
    }

    private fun onlineCutoff(): java.time.Instant =
        java.time.Instant.now().minusSeconds(staleThresholdSeconds)

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Find or create a [Customer] record from the request.
     * If the customer's zone changes (operator updated it), persist it back.
     */
    private fun resolveCustomer(request: OrderCreateRequest): Customer {
        val existing = customerRepository.findByPhoneNumber(request.customerPhone)

        val customer = if (existing.isPresent) {
            val c = existing.get()
            // Update name if it changed and is not null/blank
            if (!request.customerName.isNullOrBlank() && c.fullName != request.customerName) {
                c.fullName = request.customerName
            }
            c
        } else {
            // Auto-create user account if phone is unknown
            val userOpt = userRepository.findByPhoneNumber(request.customerPhone)
            val userId = if (userOpt.isEmpty) {
                val role = roleRepository.findByRoleName("CUSTOMER").orElse(null)
                val finalName = if (!request.customerName.isNullOrBlank()) request.customerName else "Unknown"
                val newUser = User(
                    username = request.customerPhone,
                    phoneNumber = request.customerPhone,
                    fullName = finalName,
                    roles = role?.let { mutableSetOf(it) } ?: mutableSetOf()
                )
                val savedUser = userRepository.save(newUser)
                // Keep customer_profiles in sync for mobile-app users
                val profile = CustomerProfile(
                    userId = savedUser.id!!,
                    user = savedUser,
                    displayName = finalName
                )
                customerProfileRepository.save(profile)
                savedUser.id
            } else {
                userOpt.get().id
            }

            Customer(
                userId = userId,
                fullName = request.customerName,
                phoneNumber = request.customerPhone
            )
        }

        // If caller explicitly provided a zone, update the customer record
        if (request.deliveryZoneId != null) {
            val zone = deliveryZoneRepository.findById(request.deliveryZoneId)
                .orElseThrow { ResourceNotFoundException("Delivery zone not found: ${request.deliveryZoneId}") }
            // Zone changed → clear pinned coordinates so driver will re-pin on delivery
            if (customer.deliveryZone?.id != zone.id) {
                customer.deliveryZone = zone
                customer.latitude = null
                customer.longitude = null
            }
        }

        if (request.detailedAddress != null) {
            customer.detailedAddress = request.detailedAddress
        }

        return customerRepository.save(customer)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Create order
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun createOrder(request: OrderCreateRequest): OrderResponse {
        val orderType = try {
            com.thecode007.turboxpress.entity.OrderType.valueOf(request.orderType)
        } catch (e: IllegalArgumentException) {
            com.thecode007.turboxpress.entity.OrderType.FOOD_DELIVERY
        }

        val customer = resolveCustomer(request)

        when (orderType) {
            // ── ROOM SERVICE / TAXI ──────────────────────────────────────────
            com.thecode007.turboxpress.entity.OrderType.ROOM_SERVICE,
            com.thecode007.turboxpress.entity.OrderType.TAXI -> {
                val order = Order(
                    orderType = orderType,
                    restaurant = null,
                    customer = customer,
                    totalAmount = 0.0,
                    deliveryFee = request.deliveryFee,
                    sourceName = request.sourceName,
                    destinationName = request.destinationName,
                    customDescription = request.customDescription
                )
                val savedOrder = orderRepository.save(order)

                // Auto-assign driver if pre-selected (Room Service flow)
                if (request.driverPhoneNumber != null) {
                    try {
                        val user = userRepository.findByPhoneNumber(request.driverPhoneNumber)
                        if (user.isPresent) {
                            val driverProfile = user.get().id?.let { driverProfileRepository.findById(it).orElse(null) }
                            if (driverProfile != null) {
                                savedOrder.driver = driverProfile
                                savedOrder.deliveryStatus = com.thecode007.turboxpress.entity.DeliveryStatus.ASSIGNED
                                driverProfile.status = com.thecode007.turboxpress.entity.DriverStatus.ON_DELIVERY
                                driverProfileRepository.save(driverProfile)
                                orderRepository.save(savedOrder)
                                // Notify the driver
                                notificationService.notifyDriver(savedOrder.id, request.driverPhoneNumber)
                                log.info("[createOrder] Driver ${request.driverPhoneNumber} auto-assigned to ${orderType.name} order #${savedOrder.id}")
                            }
                        }
                    } catch (e: Exception) {
                        log.warn("[createOrder] Auto-assign driver failed for order #${savedOrder.id}: ${e.message}")
                    }
                }

                val response = mapToResponse(savedOrder)
                orderEventBroadcaster.broadcast(
                    type = "ORDER_CREATED",
                    orderId = savedOrder.id,
                    status = savedOrder.status.name,
                    customerName = savedOrder.customer.fullName,
                    restaurantName = "${orderType.name.replace('_', ' ')}"
                )
                return response
            }

            // ── FOOD DELIVERY (original logic) ───────────────────────────────
            com.thecode007.turboxpress.entity.OrderType.FOOD_DELIVERY -> {
                val restaurant = restaurantRepository.findById(request.restaurantId)
                    .orElseThrow { ResourceNotFoundException("Restaurant not found: ${request.restaurantId}") }

                val order = Order(
                    orderType = orderType,
                    restaurant = restaurant,
                    customer = customer,
                    totalAmount = 0.0,
                    routeDistanceKm = request.routeDistanceKm,
                    deliveryFee = request.deliveryFee,
                    customDescription = request.customDescription,
                    customItemsCost = request.customItemsCost
                )

                val savedOrder = orderRepository.save(order)
                var totalAmount = request.customItemsCost ?: 0.0

                val orderItems = request.items.map { itemRequest ->
                    val menuItem = restaurantItemRepository.findById(itemRequest.menuItemId)
                        .orElseThrow { ResourceNotFoundException("Menu item not found: ${itemRequest.menuItemId}") }

                    if (menuItem.restaurant.id != restaurant.id) {
                        throw IllegalArgumentException("Menu item ${menuItem.id} does not belong to restaurant ${restaurant.id}")
                    }

                    val priceAtOrder = menuItem.price
                    totalAmount += priceAtOrder * itemRequest.quantity

                    OrderItem(
                        order = savedOrder,
                        menuItem = menuItem,
                        quantity = itemRequest.quantity,
                        priceAtOrder = priceAtOrder
                    )
                }

                orderItemRepository.saveAll(orderItems)
                savedOrder.totalAmount = totalAmount
                savedOrder.platformCommissionAmount = totalAmount * restaurant.commissionRate
                savedOrder.items.addAll(orderItems)
                val finalOrder = orderRepository.save(savedOrder)

                // Trigger broadcast if manual assignment is enabled.
                val isAuto = appSettingRepository.findById(1L).map { it.isAutoAssignEnabled }.orElse(true)
                log.info("[createOrder] Food Delivery order #${finalOrder.id} created. isAutoAssign=$isAuto")
                if (!isAuto) {
                    entityManager.flush()
                    broadcastNextPendingOrder()
                }

                val response = mapToResponse(finalOrder)
                orderEventBroadcaster.broadcast(
                    type = "ORDER_CREATED",
                    orderId = finalOrder.id,
                    status = finalOrder.status.name,
                    customerName = finalOrder.customer.fullName,
                    restaurantName = finalOrder.restaurant?.name ?: ""
                )
                return response
            }
        }
    }

    @CacheEvict(value = ["driver-work-pages"], allEntries = true)
    @Transactional
    fun updateOrder(id: Long, request: OrderCreateRequest): OrderResponse {
        val order = orderRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Order not found: $id") }

        val orderType = try {
            com.thecode007.turboxpress.entity.OrderType.valueOf(request.orderType)
        } catch (e: IllegalArgumentException) {
            order.orderType // keep existing type if not specified
        }

        val customer = resolveCustomer(request)
        order.customer = customer
        order.orderType = orderType
        order.sourceName = request.sourceName
        order.destinationName = request.destinationName
        order.routeDistanceKm = request.routeDistanceKm
        order.deliveryFee = request.deliveryFee
        order.customDescription = request.customDescription
        order.customItemsCost = request.customItemsCost

        if (orderType == com.thecode007.turboxpress.entity.OrderType.FOOD_DELIVERY && request.restaurantId != 0L) {
            val restaurant = restaurantRepository.findById(request.restaurantId)
                .orElseThrow { ResourceNotFoundException("Restaurant not found: ${request.restaurantId}") }
            order.restaurant = restaurant

            // Remove old items
            orderItemRepository.deleteAll(order.items)
            order.items.clear()

            var totalAmount = request.customItemsCost ?: 0.0

            val newItems = request.items.map { itemRequest ->
                val menuItem = restaurantItemRepository.findById(itemRequest.menuItemId)
                    .orElseThrow { ResourceNotFoundException("Menu item not found: ${itemRequest.menuItemId}") }

                if (menuItem.restaurant.id != restaurant.id) {
                    throw IllegalArgumentException("Menu item ${menuItem.id} does not belong to restaurant ${restaurant.id}")
                }

                val priceAtOrder = menuItem.price
                totalAmount += priceAtOrder * itemRequest.quantity

                OrderItem(
                    order = order,
                    menuItem = menuItem,
                    quantity = itemRequest.quantity,
                    priceAtOrder = priceAtOrder
                )
            }

            orderItemRepository.saveAll(newItems)
            order.items.addAll(newItems)
            order.totalAmount = totalAmount
            order.platformCommissionAmount = totalAmount * restaurant.commissionRate
        }

        val finalOrder = orderRepository.save(order)
        val response = mapToResponse(finalOrder)
        orderEventBroadcaster.broadcast(
            type = "ORDER_UPDATED",
            orderId = finalOrder.id,
            status = finalOrder.status.name,
            customerName = finalOrder.customer.fullName,
            restaurantName = finalOrder.restaurant?.name ?: orderType.name.replace('_', ' ')
        )
        return response
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reads
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getOrderById(id: Long): OrderResponse {
        val order = orderRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Order not found: $id") }
        return mapToResponse(order)
    }

    @Transactional(readOnly = true)
    fun getOrdersForRestaurant(restaurantId: Long): List<OrderResponse> {
        val orders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
        return orders.map { mapToResponse(it, includeItems = true, includePolygon = false) }
    }

    @Transactional(readOnly = true)
    fun getAllOrders(deliveredSince: java.time.Instant? = null, territoryId: Long? = null): List<OrderResponse> {
        val now = java.time.Instant.now()
        val thirtyDaysAgo = now.minus(30, java.time.temporal.ChronoUnit.DAYS)
        val earliestAllowed = now.minus(62, java.time.temporal.ChronoUnit.DAYS)
        val deliveredCutoff = when {
            deliveredSince == null -> now.minus(24, java.time.temporal.ChronoUnit.HOURS)
            deliveredSince.isBefore(earliestAllowed) -> earliestAllowed
            else -> deliveredSince
        }
        val orders = orderRepository.findOrdersForDesktopScreen(thirtyDaysAgo, deliveredCutoff)
        val filteredOrders = if (territoryId != null) {
            orders.filter { order ->
                val orderTerritoryId = order.restaurant?.territory?.id
                    ?: order.driver?.territory?.id
                    ?: order.driver?.user?.territory?.id
                orderTerritoryId == territoryId
            }
        } else {
            orders
        }
        return filteredOrders.map { mapToResponse(it, includeItems = true, includePolygon = false) }
    }

    @Transactional(readOnly = true)
    fun getArchivedOrders(
        query: String? = null,
        status: OrderStatus? = null,
        restaurantId: Long? = null,
        from: java.time.Instant? = null,
        to: java.time.Instant? = null,
        territoryId: Long? = null
    ): List<OrderResponse> {
        val cleanQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val customerPattern = cleanQuery?.let { "%${it.lowercase()}%" }

        val jpql = StringBuilder("""
            SELECT o FROM Order o
            LEFT JOIN FETCH o.customer c
            LEFT JOIN FETCH c.deliveryZone
            LEFT JOIN FETCH o.restaurant r
            LEFT JOIN FETCH o.driver d
            LEFT JOIN FETCH d.user
        """.trimIndent())

        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        if (customerPattern != null) {
            conditions.add("(c IS NOT NULL AND (LOWER(c.fullName) LIKE :customerPattern OR c.phoneNumber LIKE :customerPattern))")
            params["customerPattern"] = customerPattern
        }
        if (status != null) {
            conditions.add("o.status = :status")
            params["status"] = status
        }
        if (restaurantId != null) {
            conditions.add("(r IS NOT NULL AND r.id = :restaurantId)")
            params["restaurantId"] = restaurantId
        }
        if (from != null) {
            conditions.add("o.createdAt >= :fromInstant")
            params["fromInstant"] = from
        }
        if (to != null) {
            conditions.add("o.createdAt <= :toInstant")
            params["toInstant"] = to
        }
        if (territoryId != null) {
            conditions.add("((r IS NOT NULL AND r.territory.id = :territoryId) OR (r IS NULL AND d IS NOT NULL AND (d.territory.id = :territoryId OR d.user.territory.id = :territoryId)))")
            params["territoryId"] = territoryId
        }

        if (conditions.isNotEmpty()) {
            jpql.append(" WHERE ").append(conditions.joinToString(" AND "))
        }
        jpql.append(" ORDER BY o.createdAt DESC")

        val queryObj = entityManager.createQuery(jpql.toString(), Order::class.java)
        params.forEach { (key, value) -> queryObj.setParameter(key, value) }

        return queryObj.resultList
            .map { mapToResponse(it, includeItems = false, includePolygon = false) }
    }

    @Transactional(readOnly = true)
    fun getDriverOrderHistory(driverId: java.util.UUID): com.thecode007.turboxpress.dto.DriverWorkResponse {
        val orders = orderRepository.findByDriverIdOrderByCreatedAtDesc(driverId)
            .filter { it.status == OrderStatus.DELIVERED || it.deliveryStatus == DeliveryStatus.DELIVERED }
            .map { mapToResponse(it) }
        
        var totalOrders = 0
        var totalDeliveryFees = 0.0
        
        // Group orders by shift day (schedule runs 9:00 AM to 2:00 AM next day)
        val zoneId = java.time.ZoneId.systemDefault()
        val groupedMap = mutableMapOf<String, MutableList<com.thecode007.turboxpress.dto.OrderResponse>>()
        
        for (order in orders) {
            totalOrders++
            totalDeliveryFees += order.deliveryFee
            
            val baseInstant = order.deliveredAt ?: order.createdAt
            val z = baseInstant.atZone(zoneId)
            val businessDate = if (z.hour < 9) z.toLocalDate().minusDays(1) else z.toLocalDate()
            val dateStr = businessDate.toString()
            
            groupedMap.computeIfAbsent(dateStr) { mutableListOf() }.add(order)
        }
        
        val groupedByDay = groupedMap.map { (date, dailyOrders) ->
            com.thecode007.turboxpress.dto.DailyWorkSummary(
                date = date,
                orderCount = dailyOrders.size,
                dailyFees = dailyOrders.sumOf { it.deliveryFee },
                orders = dailyOrders
            )
        }.sortedByDescending { it.date }
        
        return com.thecode007.turboxpress.dto.DriverWorkResponse(
            totalOrders = totalOrders,
            totalDeliveryFees = totalDeliveryFees,
            groupedByDay = groupedByDay
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Driver work page — iterator pattern with server-side cache
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a single day of completed (DELIVERED) orders for the given driver.
     *
     * Results are cached in ["driver-work-pages"] keyed by [driverId + ":" + resolvedDate].
     * The cache entry is evicted automatically whenever an order for this driver
     * transitions to DELIVERED (via [updateOrderStatus] or [updateDeliveryStatus]).
     *
     * The business day boundary runs from 9:00 AM to 2:00 AM next day (matching [getDriverOrderHistory]),
     * so a shift ending at 02:00 still appears on the shift date.
     *
     * @param driverId  the requesting driver's UUID
     * @param dateStr   "YYYY-MM-DD"; null → current shift day
     */
    @Cacheable(
        value = ["driver-work-pages"],
        key = "#driverId.toString() + ':' + ((#dateStr != null && !#dateStr.isBlank()) ? #dateStr : (T(java.time.Instant).now().atZone(T(java.time.ZoneId).systemDefault()).getHour() < 9 ? T(java.time.Instant).now().atZone(T(java.time.ZoneId).systemDefault()).toLocalDate().minusDays(1).toString() : T(java.time.Instant).now().atZone(T(java.time.ZoneId).systemDefault()).toLocalDate().toString()))"
    )
    @Transactional(readOnly = true)
    fun getDriverWorkPage(driverId: java.util.UUID, dateStr: String?): DailyWorkPage {
        val zoneId = java.time.ZoneId.systemDefault()
        val nowZoned = java.time.Instant.now().atZone(zoneId)
        val currentBusinessDay = if (nowZoned.hour < 9) nowZoned.toLocalDate().minusDays(1) else nowZoned.toLocalDate()

        val targetDate: java.time.LocalDate = if (dateStr.isNullOrBlank()) {
            currentBusinessDay
        } else {
            java.time.LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        }

        // Shift window: 9:00 AM on targetDate to 2:00 AM on targetDate + 1
        val windowStart: java.time.Instant = targetDate.atTime(9, 0).atZone(zoneId).toInstant()
        val windowEnd: java.time.Instant = targetDate.plusDays(1).atTime(2, 0).atZone(zoneId).toInstant()

        // Fetch only the requested day — O(orders_that_day), not O(entire history)
        val orders = orderRepository
            .findByDriverIdAndStatusAndDeliveredAtBetweenOrderByDeliveredAtDesc(
                driverId = driverId,
                status   = OrderStatus.DELIVERED,
                start    = windowStart,
                end      = windowEnd
            )
            .map { mapToResponse(it) }

        // Two lightweight COUNT-only queries — no extra rows returned
        val hasPrevious = orderRepository.existsByDriverIdAndStatusAndDeliveredAtBefore(
            driverId = driverId,
            status   = OrderStatus.DELIVERED,
            before   = windowStart
        )
        val hasNext = orderRepository.existsByDriverIdAndStatusAndDeliveredAtGreaterThanEqual(
            driverId = driverId,
            status   = OrderStatus.DELIVERED,
            from     = windowEnd
        )

        return DailyWorkPage(
            date        = targetDate.toString(),
            orderCount  = orders.size,
            dailyFees   = orders.sumOf { it.deliveryFee },
            orders      = orders,
            hasPrevious = hasPrevious,
            hasNext     = hasNext
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Driver assignment
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun assignDriver(orderId: Long, driverPhoneNumber: String): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        val user = userRepository.findByPhoneNumber(driverPhoneNumber)
            .orElseThrow { ResourceNotFoundException("User not found with phone: $driverPhoneNumber") }
        val newDriverProfile = user.id?.let { driverProfileRepository.findById(it).orElse(null) }
            ?: throw ResourceNotFoundException("Driver profile not found with phone: $driverPhoneNumber")

        validateDriverTerritoryForOrder(newDriverProfile, order)

        // Free up the old driver if there is one and they have no other active orders
        val oldDriver = order.driver
        if (oldDriver != null && oldDriver.id != newDriverProfile.id) {
            releaseDriverIfNoActiveOrders(oldDriver, order.id)
            notificationService.notifyDriverUnassigned(order.id, oldDriver.user.phoneNumber)
        }

        order.driver = newDriverProfile
        if (order.deliveryStatus == DeliveryStatus.PENDING || order.deliveryStatus == DeliveryStatus.AT_RESTAURANT) {
            order.deliveryStatus = DeliveryStatus.ASSIGNED
        }

        newDriverProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(newDriverProfile)

        val savedOrder = orderRepository.save(order)

        savedOrder.restaurant?.owner?.phoneNumber?.let { notificationService.notifyFrontend(savedOrder.id, it) }
        notificationService.notifyDriver(savedOrder.id, driverPhoneNumber)

        val response = mapToResponse(savedOrder)
        orderEventBroadcaster.broadcast(
            type = "DRIVER_ASSIGNED",
            orderId = savedOrder.id!!,
            status = savedOrder.status.name,
            driverName = newDriverProfile.user.fullName
        )
        return response
    }

    @Transactional
    fun batchAssignDriver(orderIds: List<Long>, driverPhoneNumber: String): List<OrderResponse> {
        if (orderIds.isEmpty()) {
            throw IllegalArgumentException("Order IDs list cannot be empty")
        }

        val user = userRepository.findByPhoneNumber(driverPhoneNumber)
            .orElseThrow { ResourceNotFoundException("User not found with phone: $driverPhoneNumber") }
        val newDriverProfile = user.id?.let { driverProfileRepository.findById(it).orElse(null) }
            ?: throw ResourceNotFoundException("Driver profile not found with phone: $driverPhoneNumber")

        val orders = orderRepository.findAllById(orderIds)
        if (orders.size != orderIds.distinct().size) {
            val foundIds = orders.mapNotNull { it.id }.toSet()
            val missingIds = orderIds.filterNot { foundIds.contains(it) }
            throw ResourceNotFoundException("Orders not found: $missingIds")
        }

        val terminalOrders = orders.filter {
            it.status == OrderStatus.DELIVERED || it.status == OrderStatus.CANCELLED || it.status == OrderStatus.REJECTED
        }
        if (terminalOrders.isNotEmpty()) {
            throw IllegalStateException("Cannot assign driver to completed or cancelled orders: ${terminalOrders.map { it.id }}")
        }

        val distinctOldDrivers = mutableSetOf<DriverProfile>()
        val updatedOrders = mutableListOf<Order>()

        for (order in orders) {
            validateDriverTerritoryForOrder(newDriverProfile, order)
            val oldDriver = order.driver
            if (oldDriver != null && oldDriver.id != newDriverProfile.id) {
                distinctOldDrivers.add(oldDriver)
                notificationService.notifyDriverUnassigned(order.id, oldDriver.user.phoneNumber)
            }

            order.driver = newDriverProfile
            if (order.deliveryStatus == DeliveryStatus.PENDING || order.deliveryStatus == DeliveryStatus.AT_RESTAURANT) {
                order.deliveryStatus = DeliveryStatus.ASSIGNED
            }
            updatedOrders.add(order)
        }

        newDriverProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(newDriverProfile)

        val newlyAssignedIds = orders.mapNotNull { it.id }.toSet()
        for (oldDriver in distinctOldDrivers) {
            val activeStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.ON_THE_WAY)
            val remainingActive = orderRepository.findByDriverIdAndStatusInOrderByCreatedAtAsc(oldDriver.userId, activeStatuses)
                .filterNot { newlyAssignedIds.contains(it.id) }
            if (remainingActive.isEmpty()) {
                oldDriver.status = DriverStatus.IDLE
                driverProfileRepository.save(oldDriver)
            }
        }

        val savedOrders = orderRepository.saveAll(updatedOrders)

        for (savedOrder in savedOrders) {
            savedOrder.restaurant?.owner?.phoneNumber?.let { notificationService.notifyFrontend(savedOrder.id, it) }
            notificationService.notifyDriver(savedOrder.id, driverPhoneNumber)
            orderEventBroadcaster.broadcast(
                type = "DRIVER_ASSIGNED",
                orderId = savedOrder.id!!,
                status = savedOrder.status.name,
                driverName = newDriverProfile.user.fullName
            )
        }

        return savedOrders.map { mapToResponse(it) }
    }

    @Transactional
    fun autoAssignDriver(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        val restaurantLocation = order.restaurant?.location
            ?: throw ResourceNotFoundException("Cannot auto-assign: order has no restaurant location")
        val restaurantTerritoryId = order.restaurant?.territory?.id
        val nearestProfile = if (restaurantTerritoryId != null) {
            driverProfileRepository.findNearestIdleDriverInTerritory(
                restaurantLocation = restaurantLocation,
                territoryId = restaurantTerritoryId,
                cutoff = onlineCutoff()
            )
        } else {
            driverProfileRepository.findNearestIdleDriver(restaurantLocation, onlineCutoff())
        } ?: throw ResourceNotFoundException("No idle drivers available near the restaurant in the matching territory")

        order.driver = nearestProfile
        order.deliveryStatus = DeliveryStatus.ASSIGNED
        nearestProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(nearestProfile)

        val savedOrder = orderRepository.save(order)

        savedOrder.restaurant?.owner?.phoneNumber?.let { notificationService.notifyFrontend(savedOrder.id, it) }
        notificationService.notifyDriver(savedOrder.id, nearestProfile.user.phoneNumber)

        val response = mapToResponse(savedOrder)
        orderEventBroadcaster.broadcast(
            type = "DRIVER_ASSIGNED",
            orderId = savedOrder.id!!,
            status = savedOrder.status.name,
            driverName = nearestProfile.user.fullName
        )
        return response
    }

    @Transactional
    fun rejectOrder(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        val driverProfile = order.driver
        if (driverProfile == null) {
            // It's a broadcast order that the driver dismissed from their screen.
            return mapToResponse(order)
        }

        order.driver = null

        driverProfile.goOffline()
        driverProfileRepository.save(driverProfile)
        driverLocationService.evictPresence(driverProfile.userId)

        val savedOrder = orderRepository.save(order)

        // The order was rejected and goes back to the pool, trigger broadcast for others.
        val isAuto = appSettingRepository.findById(1L).map { it.isAutoAssignEnabled }.orElse(true)
        if (!isAuto) {
            broadcastNextPendingOrder()
        }

        // Notify the owner (admin) about the rejection
        notificationService.notifyOwnerDriverRejected(
            orderId = savedOrder.id,
            ownerPhoneNumber = savedOrder.restaurant?.owner?.phoneNumber ?: return mapToResponse(savedOrder),
            driverName = driverProfile.user.fullName
        )

        // Broadcast to desktop app for immediate Kanban update
        orderEventBroadcaster.broadcast(
            type = "DRIVER_REJECTED",
            orderId = savedOrder.id!!,
            status = savedOrder.status.name,
            driverName = driverProfile.user.fullName
        )

        return mapToResponse(savedOrder)
    }

    @Transactional
    fun broadcastNextPendingOrder() {
        log.info("[broadcastNextPendingOrder] Called — querying unassigned orders...")
        println(">>> [broadcastNextPendingOrder] Called — querying unassigned orders...")
        val targetStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP)
        val pendingOrders = orderRepository.findByDriverIsNullAndStatusInOrderByCreatedAtAsc(targetStatuses)
        log.info("[broadcastNextPendingOrder] Found ${pendingOrders.size} unassigned order(s): ${pendingOrders.map { it.id }}")
        println(">>> [broadcastNextPendingOrder] Found ${pendingOrders.size} unassigned order(s): ${pendingOrders.map { it.id }}")

        val nextOrder = pendingOrders.firstOrNull()
        if (nextOrder == null) {
            log.info("[broadcastNextPendingOrder] No eligible unassigned orders — broadcast skipped.")
            println(">>> [broadcastNextPendingOrder] No eligible unassigned orders — broadcast skipped.")
            return
        }
        log.info("[broadcastNextPendingOrder] Next order to broadcast: #${nextOrder.id} (status=${nextOrder.status})")
        println(">>> [broadcastNextPendingOrder] Next order: #${nextOrder.id} (status=${nextOrder.status})")

        val idleDrivers = driverProfileRepository.findAllApprovedOnlineIdleDriversWithUser(onlineCutoff())
            .filter { canDriverServeOrder(it, nextOrder) }
        val phones = idleDrivers.mapNotNull { it.user.phoneNumber }
        log.info("[broadcastNextPendingOrder] Found ${phones.size} ONLINE+IDLE driver(s): $phones")
        println(">>> [broadcastNextPendingOrder] Found ${phones.size} ONLINE+IDLE driver(s): $phones")
        if (phones.isNotEmpty()) {
            notificationService.broadcastOrderToDrivers(nextOrder.id, phones)
            log.info("[broadcastNextPendingOrder] Notification sent for order #${nextOrder.id} to ${phones.size} driver(s).")
            println(">>> [broadcastNextPendingOrder] FCM sent for order #${nextOrder.id} to ${phones.size} driver(s).")
        } else {
            log.warn("[broadcastNextPendingOrder] No ONLINE+IDLE drivers found — broadcast skipped for order #${nextOrder.id}.")
            println(">>> [broadcastNextPendingOrder] No ONLINE+IDLE drivers — broadcast skipped for order #${nextOrder.id}.")
        }
    }

    @Transactional
    fun acceptBroadcastOrder(orderId: Long, driverPhoneNumber: String): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        if (order.driver != null) {
            throw IllegalStateException("ORDER_ALREADY_TAKEN")
        }

        val user = userRepository.findByPhoneNumber(driverPhoneNumber)
            .orElseThrow { ResourceNotFoundException("User not found with phone: $driverPhoneNumber") }
        val newDriverProfile = user.id?.let { driverProfileRepository.findByUserIdWithUser(it).orElse(null) }
            ?: throw ResourceNotFoundException("Driver profile not found with phone: $driverPhoneNumber")

        if (!newDriverProfile.isOnlineAndAvailable(onlineCutoff())) {
            throw IllegalStateException("Driver is not online and available to accept orders")
        }

        if (!canDriverServeOrder(newDriverProfile, order)) {
            throw IllegalStateException("Driver territory does not match restaurant territory")
        }

        order.driver = newDriverProfile
        order.deliveryStatus = DeliveryStatus.ASSIGNED

        newDriverProfile.status = DriverStatus.ON_DELIVERY
        newDriverProfile.recordHeartbeat()
        driverProfileRepository.save(newDriverProfile)

        val savedOrder = orderRepository.save(order)

        savedOrder.restaurant?.owner?.phoneNumber?.let { notificationService.notifyFrontend(savedOrder.id, it) }
        notificationService.notifyDriver(savedOrder.id, driverPhoneNumber)

        // Dismiss the order from all other drivers' screens
        val otherDriverPhones = driverProfileRepository.findAllApprovedOnlineIdleDriversWithUser(onlineCutoff())
            .mapNotNull { it.user.phoneNumber }
        notificationService.notifyBroadcastOrderTaken(savedOrder.id, driverPhoneNumber, otherDriverPhones)

        // Queue the next broadcast
        broadcastNextPendingOrder()

        val response = mapToResponse(savedOrder)
        orderEventBroadcaster.broadcast(
            type = "DRIVER_ASSIGNED",
            orderId = savedOrder.id!!,
            status = savedOrder.status.name,
            driverName = newDriverProfile.user.fullName
        )
        return response
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status update
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Evict the driver's cached work page whenever an order is delivered,
     * so the driver immediately sees the new entry on their next open.
     * We evict ALL entries for this driver (allEntries = false is not enough
     * because we don't know which date the order will appear on at call time).
     */
    @CacheEvict(value = ["driver-work-pages"], allEntries = true)
    @Transactional
    fun updateOrderStatus(orderId: Long, status: OrderStatus): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        if (status == OrderStatus.ON_THE_WAY && order.driver == null) {
            throw IllegalArgumentException("Cannot update status to ON_THE_WAY without an assigned driver")
        }

        val oldStatus = order.status
        order.status = status

        val now = java.time.Instant.now()
        when (status) {
            OrderStatus.ACCEPTED, OrderStatus.PREPARING -> if (order.acceptedAt == null) order.acceptedAt = now
            OrderStatus.READY_FOR_PICKUP -> {
                if (order.acceptedAt == null) order.acceptedAt = order.createdAt
                if (order.readyAt == null) order.readyAt = now
                order.driver?.user?.phoneNumber?.let { driverPhone ->
                    notificationService.notifyDriverOrderReady(order.id, driverPhone)
                }
            }
            OrderStatus.ON_THE_WAY -> {
                if (order.acceptedAt == null) order.acceptedAt = order.createdAt
                if (order.readyAt == null) order.readyAt = now
                if (order.pickedUpAt == null) order.pickedUpAt = now
                order.deliveryStatus = DeliveryStatus.ON_THE_WAY
            }
            OrderStatus.DELIVERED -> {
                if (order.acceptedAt == null) order.acceptedAt = order.createdAt
                if (order.readyAt == null) order.readyAt = order.pickedUpAt ?: now
                if (order.deliveredAt == null) order.deliveredAt = now
                order.deliveryStatus = DeliveryStatus.DELIVERED
            }
            OrderStatus.CANCELLED -> {
                order.deliveryStatus = DeliveryStatus.CANCELLED
            }
            else -> {}
        }

        // Ledger balancing has been removed from restaurant based on new business rule

        // DELIVERED → pin exact coordinates on the customer record
        if (status == OrderStatus.DELIVERED) {
            order.driver?.let { driverProfile ->
                if (driverProfile.currentLocation != null) {
                    val customer = order.customer
                    customer.latitude = driverProfile.currentLocation!!.y
                    customer.longitude = driverProfile.currentLocation!!.x
                    customerRepository.save(customer)
                }
            }
        }

        // Trigger broadcast if an unassigned order is accepted/progressed by the restaurant
        if (order.driver == null && (status == OrderStatus.ACCEPTED || status == OrderStatus.PREPARING || status == OrderStatus.READY_FOR_PICKUP)) {
            val setting = appSettingRepository.findById(1L).orElse(null)
            if (setting != null && !setting.isAutoAssignEnabled) {
                broadcastNextPendingOrder()
            }
        }

        // Free up driver
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) {
            releaseDriverIfNoActiveOrders(order.driver, order.id)
        }

        val savedOrder = orderRepository.save(order)
        val response = mapToResponse(savedOrder)
        orderEventBroadcaster.broadcast(
            type = "ORDER_STATUS_CHANGED",
            orderId = savedOrder.id!!,
            status = savedOrder.status.name,
            customerName = savedOrder.customer.fullName,
            restaurantName = savedOrder.restaurant?.name ?: savedOrder.orderType.name.replace('_', ' ')
        )
        return response
    }

    @Transactional
    fun setPickupPhoto(orderId: Long, photoUrl: String): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }
        order.pickupPhotoUrl = photoUrl
        val savedOrder = orderRepository.save(order)
        val response = mapToResponse(savedOrder)
        orderEventBroadcaster.broadcast(
            type = "ORDER_UPDATED",
            orderId = savedOrder.id,
            status = savedOrder.status.name,
            customerName = savedOrder.customer.fullName,
            restaurantName = savedOrder.restaurant?.name ?: savedOrder.orderType.name.replace('_', ' ')
        )
        return response
    }

    /** Evict cached work pages when delivery status changes (e.g. DELIVERED). */
    @CacheEvict(value = ["driver-work-pages"], allEntries = true)
    @Transactional
    fun updateDeliveryStatus(orderId: Long, status: DeliveryStatus, photoUrl: String? = null): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        if (!photoUrl.isNullOrBlank()) {
            order.pickupPhotoUrl = photoUrl
        }

        order.deliveryStatus = status

        val now = java.time.Instant.now()
        when (status) {
            DeliveryStatus.AT_RESTAURANT -> {
                if (order.driverArrivedAtRestaurantAt == null) order.driverArrivedAtRestaurantAt = now
            }
            DeliveryStatus.ON_THE_WAY -> {
                if (order.pickupPhotoUrl.isNullOrBlank()) {
                    throw IllegalArgumentException("Pickup photo is required before marking order as picked up")
                }
                if (order.acceptedAt == null) order.acceptedAt = order.createdAt
                if (order.readyAt == null) order.readyAt = order.driverArrivedAtRestaurantAt ?: now
                if (order.pickedUpAt == null) order.pickedUpAt = now
                order.status = OrderStatus.ON_THE_WAY
            }
            DeliveryStatus.DELIVERED -> {
                if (order.acceptedAt == null) order.acceptedAt = order.createdAt
                if (order.readyAt == null) order.readyAt = order.pickedUpAt ?: order.driverArrivedAtRestaurantAt ?: now
                if (order.deliveredAt == null) order.deliveredAt = now
                order.status = OrderStatus.DELIVERED
                
                // DELIVERED → pin exact coordinates on the customer record
                order.driver?.let { driverProfile ->
                    if (driverProfile.currentLocation != null) {
                        val customer = order.customer
                        customer.latitude = driverProfile.currentLocation!!.y
                        customer.longitude = driverProfile.currentLocation!!.x
                        customerRepository.save(customer)
                    }
                }
                
                // Free up driver
                releaseDriverIfNoActiveOrders(order.driver, order.id)
            }
            DeliveryStatus.CANCELLED -> {
                order.status = OrderStatus.CANCELLED
                
                // Free up driver
                releaseDriverIfNoActiveOrders(order.driver, order.id)
            }
            else -> {}
        }

        val savedOrder = orderRepository.save(order)
        val response = mapToResponse(savedOrder)
        orderEventBroadcaster.broadcast(
            type = "DELIVERY_STATUS_CHANGED",
            orderId = savedOrder.id!!,
            status = savedOrder.deliveryStatus.name,
            customerName = savedOrder.customer.fullName,
            restaurantName = savedOrder.restaurant?.name ?: savedOrder.orderType.name.replace('_', ' ')
        )
        return response
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Active delivery query
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun getActiveDeliveriesForDriver(driverId: java.util.UUID): List<OrderResponse> {
        val targetStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.ON_THE_WAY)
        val orders = orderRepository.findByDriverIdAndStatusInOrderByCreatedAtAsc(driverId, targetStatuses)

        val driverProfile = driverProfileRepository.findByUserIdWithUser(driverId).orElse(null)
        if (driverProfile != null && driverProfile.onlineStatus == com.thecode007.turboxpress.entity.OnlineStatus.ONLINE) {
            driverLocationService.recordHeartbeat(driverId)
        }

        if (orders.isNotEmpty()) {
            return orders.map { mapToResponse(it) }
        }

        // Fallback: If in manual mode, and driver is ONLINE & IDLE, serve the broadcast queue top item
        val setting = appSettingRepository.findById(1L).orElse(null)
        if (setting != null && !setting.isAutoAssignEnabled) {
            if (driverProfile != null && driverProfile.isOnlineAndAvailable(onlineCutoff())) {
                val broadcastStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP)
                val pendingOrders = orderRepository.findByDriverIsNullAndStatusInOrderByCreatedAtAsc(broadcastStatuses)
                val nextOrder = pendingOrders.firstOrNull { canDriverServeOrder(driverProfile, it) }
                if (nextOrder != null) {
                    return listOf(mapToResponse(nextOrder))
                }
            }
        }

        return emptyList()
    }

    @Transactional
    fun getActiveDeliveryForDriver(driverId: java.util.UUID): OrderResponse? {
        return getActiveDeliveriesForDriver(driverId).firstOrNull()
    }

    private fun releaseDriverIfNoActiveOrders(driverProfile: DriverProfile?, currentOrderId: Long?) {
        if (driverProfile == null) return
        val activeStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.ON_THE_WAY)
        val remainingActive = orderRepository.findByDriverIdAndStatusInOrderByCreatedAtAsc(driverProfile.userId, activeStatuses)
            .filter { it.id != currentOrderId }
        if (remainingActive.isEmpty()) {
            driverProfile.status = DriverStatus.IDLE
            driverProfileRepository.save(driverProfile)

            // Driver is now IDLE, they can receive the next broadcast
            val setting = appSettingRepository.findById(1L).orElse(null)
            if (setting != null && !setting.isAutoAssignEnabled) {
                broadcastNextPendingOrder()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun canDriverServeOrder(driverProfile: DriverProfile, order: Order): Boolean {
        val restaurantTerritoryId = order.restaurant?.territory?.id ?: return true
        val driverTerritoryId = driverProfile.territory?.id ?: driverProfile.user.territory?.id
        return driverTerritoryId == restaurantTerritoryId
    }

    private fun validateDriverTerritoryForOrder(driverProfile: DriverProfile, order: Order) {
        val restaurantTerritory = order.restaurant?.territory ?: return
        val driverTerritory = driverProfile.territory ?: driverProfile.user.territory
        if (driverTerritory != null && driverTerritory.id != restaurantTerritory.id) {
            throw IllegalArgumentException(
                "Driver '${driverProfile.displayName ?: driverProfile.user.fullName}' belongs to territory '${driverTerritory.name}' and cannot serve restaurant '${order.restaurant?.name}' in territory '${restaurantTerritory.name}'"
            )
        }
    }

    private fun mapToResponse(
        order: Order,
        includeItems: Boolean = true,
        includePolygon: Boolean = false
    ): OrderResponse {
        val customer = order.customer
        // Effective coordinates: exact pin takes priority over zone centroid
        val effectiveLat = customer.latitude ?: customer.deliveryZone?.polygon?.centroid?.y
        val effectiveLng = customer.longitude ?: customer.deliveryZone?.polygon?.centroid?.x

        val zonePolygon = if (includePolygon) {
            customer.deliveryZone?.polygon?.coordinates?.map {
                RoutePointDto(lat = it.y, lng = it.x)
            }
        } else null

        val resolvedTerritory = order.restaurant?.territory
            ?: order.driver?.territory
            ?: order.driver?.user?.territory

        return OrderResponse(
            id = order.id,
            orderType = order.orderType.name,
            restaurantId = order.restaurant?.id,
            restaurantName = order.restaurant?.name ?: order.orderType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
            territoryId = resolvedTerritory?.id,
            territoryName = resolvedTerritory?.name,
            driverPhoneNumber = order.driver?.user?.phoneNumber,
            driverFullName = order.driver?.displayName ?: order.driver?.user?.fullName,
            driverId = order.driver?.userId?.toString(),
            status = order.status,
            deliveryStatus = order.deliveryStatus,
            driverArrivedAtRestaurantAt = order.driverArrivedAtRestaurantAt,
            totalAmount = order.totalAmount,
            items = if (includeItems) order.items.map {
                OrderItemResponse(
                    id = it.id,
                    menuItemId = it.menuItem.id,
                    menuItemTitle = it.menuItem.title,
                    quantity = it.quantity,
                    priceAtOrder = it.priceAtOrder
                )
            } else emptyList(),
            customDescription = order.customDescription,
            customItemsCost = order.customItemsCost,
            customerId = customer.id,
            customerName = customer.fullName,
            customerPhone = customer.phoneNumber,
            deliveryZoneId = customer.deliveryZone?.id,
            deliveryZoneName = customer.deliveryZone?.name,
            deliveryZonePolygon = zonePolygon,
            latitude = effectiveLat,
            longitude = effectiveLng,
            detailedAddress = customer.detailedAddress,
            restaurantLat = order.restaurant?.location?.y,
            restaurantLng = order.restaurant?.location?.x,
            driverLat = order.driver?.currentLocation?.y,
            driverLng = order.driver?.currentLocation?.x,
            routeDistanceKm = order.routeDistanceKm,
            deliveryFee = order.deliveryFee,
            createdAt = order.createdAt,
            acceptedAt = order.acceptedAt,
            readyAt = order.readyAt,
            pickedUpAt = order.pickedUpAt,
            deliveredAt = order.deliveredAt,
            pickupPhotoUrl = order.pickupPhotoUrl,
            locationMethod = if (customer.deliveryZone != null) "DELIVERY_ZONE" else "WHATSAPP_LINK",
            sourceName = order.sourceName,
            destinationName = order.destinationName
        )
    }
}
