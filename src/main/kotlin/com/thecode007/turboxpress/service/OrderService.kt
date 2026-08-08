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
    private val entityManager: EntityManager
) {

    companion object {
        private val log = LoggerFactory.getLogger(OrderService::class.java)
    }

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
    fun getAllOrders(): List<OrderResponse> {
        val thirtyDaysAgo = java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS)
        val twentyFourHoursAgo = java.time.Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS)
        return orderRepository.findOrdersForDesktopScreen(thirtyDaysAgo, twentyFourHoursAgo)
            .map { mapToResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getDriverOrderHistory(driverId: java.util.UUID): com.thecode007.turboxpress.dto.DriverWorkResponse {
        val orders = orderRepository.findByDriverIdOrderByCreatedAtDesc(driverId).map { mapToResponse(it) }
        
        var totalOrders = 0
        var totalDeliveryFees = 0.0
        
        // Group orders by day, with a -2 hours offset so the day ends at 2 AM
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(java.time.ZoneOffset.UTC)
        val groupedMap = mutableMapOf<String, MutableList<com.thecode007.turboxpress.dto.OrderResponse>>()
        
        for (order in orders) {
            totalOrders++
            totalDeliveryFees += order.deliveryFee
            
            val adjustedInstant = order.createdAt.minus(2, java.time.temporal.ChronoUnit.HOURS)
            val dateStr = formatter.format(adjustedInstant)
            
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
     * The business day boundary is shifted -2 h (matching [getDriverOrderHistory]),
     * so a shift ending at 02:00 UTC still appears on the previous calendar date.
     *
     * @param driverId  the requesting driver's UUID
     * @param dateStr   "YYYY-MM-DD" in UTC-adjusted time; null → today
     */
    @Cacheable(value = ["driver-work-pages"], key = "#driverId.toString() + ':' + (#dateStr ?: T(java.time.LocalDate).now(T(java.time.ZoneOffset).UTC).toString())")
    @Transactional(readOnly = true)
    fun getDriverWorkPage(driverId: java.util.UUID, dateStr: String?): DailyWorkPage {
        // Resolve the target date — default to today in UTC
        val targetDate: java.time.LocalDate = if (dateStr.isNullOrBlank()) {
            java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        } else {
            java.time.LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        }

        // Shift the day window -2 h so a late shift ending at 02:00 UTC sits on the
        // previous calendar day (matches the existing grouping logic in getDriverOrderHistory)
        val windowStart: java.time.Instant = targetDate.atStartOfDay(java.time.ZoneOffset.UTC)
            .plusHours(2).toInstant()
        val windowEnd: java.time.Instant = targetDate.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC)
            .plusHours(2).toInstant()

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

        // Free up the old driver if there is one
        val oldDriver = order.driver
        if (oldDriver != null && oldDriver.id != newDriverProfile.id) {
            oldDriver.status = DriverStatus.IDLE
            driverProfileRepository.save(oldDriver)
            
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
    fun autoAssignDriver(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        val restaurantLocation = order.restaurant?.location
            ?: throw ResourceNotFoundException("Cannot auto-assign: order has no restaurant location")
        val nearestProfile = driverProfileRepository.findNearestIdleDriver(restaurantLocation)
            ?: throw ResourceNotFoundException("No idle drivers available near the restaurant")

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

        driverProfile.onlineStatus = com.thecode007.turboxpress.entity.OnlineStatus.OFFLINE
        driverProfile.status = DriverStatus.IDLE
        driverProfileRepository.save(driverProfile)

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

        val idleDrivers = driverProfileRepository.findAllApprovedOnlineIdleDriversWithUser()
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
        val newDriverProfile = user.id?.let { driverProfileRepository.findById(it).orElse(null) }
            ?: throw ResourceNotFoundException("Driver profile not found with phone: $driverPhoneNumber")

        if (newDriverProfile.status != DriverStatus.IDLE) {
            throw IllegalStateException("Driver is not IDLE")
        }

        order.driver = newDriverProfile
        order.deliveryStatus = DeliveryStatus.ASSIGNED

        newDriverProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(newDriverProfile)

        val savedOrder = orderRepository.save(order)

        savedOrder.restaurant?.owner?.phoneNumber?.let { notificationService.notifyFrontend(savedOrder.id, it) }
        notificationService.notifyDriver(savedOrder.id, driverPhoneNumber)

        // Dismiss the order from all other drivers' screens
        val otherDriverPhones = driverProfileRepository.findAllApprovedOnlineIdleDriversWithUser()
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
    @CacheEvict(value = ["driver-work-pages"], key = "#result.driverId + ':' + T(java.time.LocalDate).now(T(java.time.ZoneOffset).UTC).toString()", condition = "#result.driverId != null")
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
                if (order.readyAt == null) order.readyAt = now
                order.driver?.user?.phoneNumber?.let { driverPhone ->
                    notificationService.notifyDriverOrderReady(order.id, driverPhone)
                }
            }
            OrderStatus.ON_THE_WAY -> if (order.pickedUpAt == null) order.pickedUpAt = now
            OrderStatus.DELIVERED -> if (order.deliveredAt == null) order.deliveredAt = now
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
            order.driver?.let { profile ->
                profile.status = DriverStatus.IDLE
                driverProfileRepository.save(profile)
            }
            
            // Driver is now IDLE, they can receive the next broadcast
            val setting = appSettingRepository.findById(1L).orElse(null)
            if (setting != null && !setting.isAutoAssignEnabled) {
                broadcastNextPendingOrder()
            }
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

    /** Evict cached work pages when delivery status changes (e.g. DELIVERED). */
    @CacheEvict(value = ["driver-work-pages"], key = "#result.driverId + ':' + T(java.time.LocalDate).now(T(java.time.ZoneOffset).UTC).toString()", condition = "#result.driverId != null")
    @Transactional
    fun updateDeliveryStatus(orderId: Long, status: DeliveryStatus): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        order.deliveryStatus = status

        val now = java.time.Instant.now()
        when (status) {
            DeliveryStatus.AT_RESTAURANT -> {
                if (order.driverArrivedAtRestaurantAt == null) order.driverArrivedAtRestaurantAt = now
            }
            DeliveryStatus.ON_THE_WAY -> {
                if (order.pickedUpAt == null) order.pickedUpAt = now
                order.status = OrderStatus.ON_THE_WAY
            }
            DeliveryStatus.DELIVERED -> {
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
                order.driver?.let { profile ->
                    profile.status = DriverStatus.IDLE
                    driverProfileRepository.save(profile)
                }
                
                // Queue next broadcast
                val setting = appSettingRepository.findById(1L).orElse(null)
                if (setting != null && !setting.isAutoAssignEnabled) {
                    broadcastNextPendingOrder()
                }
            }
            DeliveryStatus.CANCELLED -> {
                order.status = OrderStatus.CANCELLED
                
                // Free up driver
                order.driver?.let { profile ->
                    profile.status = DriverStatus.IDLE
                    driverProfileRepository.save(profile)
                }
                
                // Queue next broadcast
                val setting = appSettingRepository.findById(1L).orElse(null)
                if (setting != null && !setting.isAutoAssignEnabled) {
                    broadcastNextPendingOrder()
                }
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

    @Transactional(readOnly = true)
    fun getActiveDeliveryForDriver(driverId: java.util.UUID): OrderResponse? {
        val targetStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.ON_THE_WAY)
        val orders = orderRepository.findByDriverIdAndStatusInOrderByCreatedAtDesc(driverId, targetStatuses)
        val assignedOrder = orders.firstOrNull()?.let { mapToResponse(it) }

        if (assignedOrder != null) {
            return assignedOrder
        }

        // Fallback: If in manual mode, and driver is ONLINE & IDLE, serve the broadcast queue top item
        val setting = appSettingRepository.findById(1L).orElse(null)
        if (setting != null && !setting.isAutoAssignEnabled) {
            val driverProfile = driverProfileRepository.findById(driverId).orElse(null)
            if (driverProfile != null && driverProfile.status == DriverStatus.IDLE && driverProfile.onlineStatus == com.thecode007.turboxpress.entity.OnlineStatus.ONLINE) {
                val broadcastStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP)
                val pendingOrders = orderRepository.findByDriverIsNullAndStatusInOrderByCreatedAtAsc(broadcastStatuses)
                val nextOrder = pendingOrders.firstOrNull()
                if (nextOrder != null) {
                    return mapToResponse(nextOrder)
                }
            }
        }

        return null
    }

    @Transactional
    fun assignDriverFromScheduler(orderId: Long, driverProfileId: java.util.UUID): Boolean {
        val order = orderRepository.findById(orderId).orElse(null) ?: return false

        if (order.driver != null) return false

        val driverProfile = driverProfileRepository.findById(driverProfileId).orElse(null) ?: return false

        order.driver = driverProfile
        order.deliveryStatus = DeliveryStatus.ASSIGNED
        orderRepository.save(order)

        driverProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(driverProfile)

        order.restaurant?.owner?.phoneNumber?.let { notificationService.notifyFrontend(order.id, it) }
        notificationService.notifyDriver(order.id, driverProfile.user.phoneNumber)

        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun mapToResponse(order: Order): OrderResponse {
        val customer = order.customer
        // Effective coordinates: exact pin takes priority over zone centroid
        val effectiveLat = customer.latitude ?: customer.deliveryZone?.polygon?.centroid?.y
        val effectiveLng = customer.longitude ?: customer.deliveryZone?.polygon?.centroid?.x

        val zonePolygon = customer.deliveryZone?.polygon?.coordinates?.map {
            RoutePointDto(lat = it.y, lng = it.x)
        }

        return OrderResponse(
            id = order.id,
            orderType = order.orderType.name,
            restaurantId = order.restaurant?.id,
            restaurantName = order.restaurant?.name ?: order.orderType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
            driverPhoneNumber = order.driver?.user?.phoneNumber,
            driverFullName = order.driver?.displayName ?: order.driver?.user?.fullName,
            driverId = order.driver?.userId?.toString(),
            status = order.status,
            deliveryStatus = order.deliveryStatus,
            driverArrivedAtRestaurantAt = order.driverArrivedAtRestaurantAt,
            totalAmount = order.totalAmount,
            items = order.items.map {
                OrderItemResponse(
                    id = it.id,
                    menuItemId = it.menuItem.id,
                    menuItemTitle = it.menuItem.title,
                    quantity = it.quantity,
                    priceAtOrder = it.priceAtOrder
                )
            },
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
            locationMethod = if (customer.deliveryZone != null) "DELIVERY_ZONE" else "WHATSAPP_LINK",
            sourceName = order.sourceName,
            destinationName = order.destinationName
        )
    }
}
