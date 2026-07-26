package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.*
import com.thecode007.turboxpress.entity.*
import com.thecode007.turboxpress.exception.ResourceNotFoundException
import com.thecode007.turboxpress.repository.*
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
    private val orderEventBroadcaster: OrderEventBroadcaster
) {

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
        val restaurant = restaurantRepository.findById(request.restaurantId)
            .orElseThrow { ResourceNotFoundException("Restaurant not found: ${request.restaurantId}") }

        val customer = resolveCustomer(request)

        val order = Order(
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

        // Trigger broadcast if manual assignment is enabled
        val isAuto = appSettingRepository.findById(1L).map { it.isAutoAssignEnabled }.orElse(true)
        if (!isAuto) {
            broadcastNextPendingOrder()
        }

        val response = mapToResponse(finalOrder)
        orderEventBroadcaster.broadcast(
            type = "ORDER_CREATED",
            orderId = finalOrder.id!!,
            status = finalOrder.status.name,
            customerName = finalOrder.customer.fullName,
            restaurantName = finalOrder.restaurant.name
        )
        return response
    }

    @Transactional
    fun updateOrder(id: Long, request: OrderCreateRequest): OrderResponse {
        val order = orderRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Order not found: $id") }

        val restaurant = restaurantRepository.findById(request.restaurantId)
            .orElseThrow { ResourceNotFoundException("Restaurant not found: ${request.restaurantId}") }

        val customer = resolveCustomer(request)

        order.restaurant = restaurant
        order.customer = customer
        order.routeDistanceKm = request.routeDistanceKm
        order.deliveryFee = request.deliveryFee
        order.customDescription = request.customDescription
        order.customItemsCost = request.customItemsCost

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

        val finalOrder = orderRepository.save(order)
        val response = mapToResponse(finalOrder)
        orderEventBroadcaster.broadcast(
            type = "ORDER_UPDATED",
            orderId = finalOrder.id!!,
            status = finalOrder.status.name,
            customerName = finalOrder.customer.fullName,
            restaurantName = finalOrder.restaurant.name
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
        return orderRepository.findByCreatedAtAfterOrderByCreatedAtDesc(thirtyDaysAgo).map { mapToResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getDriverOrderHistory(driverId: java.util.UUID): List<OrderResponse> {
        return orderRepository.findByDriverIdOrderByCreatedAtDesc(driverId).map { mapToResponse(it) }
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

        notificationService.notifyFrontend(savedOrder.id, savedOrder.restaurant.owner.phoneNumber)
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

        val nearestProfile = driverProfileRepository.findNearestIdleDriver(order.restaurant.location)
            ?: throw ResourceNotFoundException("No idle drivers available near the restaurant")

        order.driver = nearestProfile
        order.deliveryStatus = DeliveryStatus.ASSIGNED
        nearestProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(nearestProfile)

        val savedOrder = orderRepository.save(order)

        notificationService.notifyFrontend(savedOrder.id, savedOrder.restaurant.owner.phoneNumber)
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
            ownerPhoneNumber = savedOrder.restaurant.owner.phoneNumber,
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
        val targetStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP)
        val pendingOrders = orderRepository.findByDriverIsNullAndStatusInOrderByCreatedAtAsc(targetStatuses)
        val nextOrder = pendingOrders.firstOrNull() ?: return

        // Use JOIN FETCH query to eagerly load user phone numbers.
        // Only APPROVED+ONLINE+IDLE drivers receive the broadcast.
        val idleDrivers = driverProfileRepository.findAllApprovedOnlineIdleDriversWithUser()
        val phones = idleDrivers.mapNotNull { it.user.phoneNumber }
        println("Broadcasting order #${nextOrder.id} to ${phones.size} drivers: $phones")
        if (phones.isNotEmpty()) {
            notificationService.broadcastOrderToDrivers(nextOrder.id, phones)
        } else {
            println("No ONLINE+IDLE drivers found. Broadcast skipped for order #${nextOrder.id}")
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

        notificationService.notifyFrontend(savedOrder.id, savedOrder.restaurant.owner.phoneNumber)
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
            restaurantName = savedOrder.restaurant.name
        )
        return response
    }

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
            restaurantName = savedOrder.restaurant.name
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

        notificationService.notifyFrontend(order.id, order.restaurant.owner.phoneNumber)
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
            restaurantId = order.restaurant.id,
            restaurantName = order.restaurant.name,
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
            restaurantLat = order.restaurant.location.y,
            restaurantLng = order.restaurant.location.x,
            driverLat = order.driver?.currentLocation?.y,
            driverLng = order.driver?.currentLocation?.x,
            routeDistanceKm = order.routeDistanceKm,
            deliveryFee = order.deliveryFee,
            createdAt = order.createdAt,
            acceptedAt = order.acceptedAt,
            readyAt = order.readyAt,
            pickedUpAt = order.pickedUpAt,
            deliveredAt = order.deliveredAt,
            locationMethod = if (customer.deliveryZone != null) "DELIVERY_ZONE" else "WHATSAPP_LINK"
        )
    }
}
