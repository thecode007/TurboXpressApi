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
    private val simulationService: SimulationService,
    private val deliveryZoneRepository: DeliveryZoneRepository,
    private val customerRepository: CustomerRepository,
    private val customerProfileRepository: CustomerProfileRepository,
    private val roleRepository: RoleRepository
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
            // Update name if it changed
            if (request.customerName.isNotBlank() && c.fullName != request.customerName) {
                c.fullName = request.customerName
            }
            c
        } else {
            // Auto-create user account if phone is unknown
            val userOpt = userRepository.findByPhoneNumber(request.customerPhone)
            val userId = if (userOpt.isEmpty) {
                val role = roleRepository.findByRoleName("CUSTOMER").orElse(null)
                val newUser = User(
                    username = request.customerPhone,
                    phoneNumber = request.customerPhone,
                    fullName = request.customerName,
                    roles = role?.let { mutableSetOf(it) } ?: mutableSetOf()
                )
                val savedUser = userRepository.save(newUser)
                // Keep customer_profiles in sync for mobile-app users
                val profile = CustomerProfile(
                    userId = savedUser.id!!,
                    user = savedUser,
                    displayName = request.customerName
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
            deliveryFee = request.deliveryFee
        )

        val savedOrder = orderRepository.save(order)
        var totalAmount = 0.0

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

        return mapToResponse(finalOrder)
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

        // Remove old items
        orderItemRepository.deleteAll(order.items)
        order.items.clear()

        var totalAmount = 0.0

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
        return mapToResponse(finalOrder)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reads
    // ─────────────────────────────────────────────────────────────────────────

    fun getOrderById(id: Long): OrderResponse {
        val order = orderRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Order not found: $id") }
        return mapToResponse(order)
    }

    fun getAllOrders(): List<OrderResponse> {
        val thirtyDaysAgo = java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS)
        return orderRepository.findByCreatedAtAfterOrderByCreatedAtDesc(thirtyDaysAgo).map { mapToResponse(it) }
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
        val driverProfile = user.id?.let { driverProfileRepository.findById(it).orElse(null) }
            ?: throw ResourceNotFoundException("Driver profile not found with phone: $driverPhoneNumber")

        order.driver = driverProfile
        order.status = OrderStatus.ACCEPTED

        driverProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(driverProfile)

        val savedOrder = orderRepository.save(order)

        notificationService.notifyFrontend(savedOrder.id, savedOrder.restaurant.owner.phoneNumber)
        notificationService.notifyDriver(savedOrder.id, driverPhoneNumber)

        startSimulationToRestaurant(savedOrder, driverProfile)

        return mapToResponse(savedOrder)
    }

    @Transactional
    fun autoAssignDriver(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        val nearestProfile = driverProfileRepository.findNearestIdleDriver(order.restaurant.location)
            ?: throw ResourceNotFoundException("No idle drivers available near the restaurant")

        order.driver = nearestProfile
        order.status = OrderStatus.ACCEPTED
        nearestProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(nearestProfile)

        val savedOrder = orderRepository.save(order)

        notificationService.notifyFrontend(savedOrder.id, savedOrder.restaurant.owner.phoneNumber)
        notificationService.notifyDriver(savedOrder.id, nearestProfile.user.phoneNumber)

        startSimulationToRestaurant(savedOrder, nearestProfile)

        return mapToResponse(savedOrder)
    }

    @Transactional
    fun rejectOrder(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        val driverProfile = order.driver
            ?: throw IllegalStateException("Order is not assigned to a driver")

        order.driver = null

        driverProfile.onlineStatus = OnlineStatus.OFFLINE
        driverProfile.status = DriverStatus.IDLE
        driverProfileRepository.save(driverProfile)

        return mapToResponse(orderRepository.save(order))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status update
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun updateOrderStatus(orderId: Long, status: OrderStatus): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found: $orderId") }

        val oldStatus = order.status
        order.status = status

        // Balance ledger
        val wasFinanciallyImpacted = oldStatus == OrderStatus.ON_THE_WAY || oldStatus == OrderStatus.DELIVERED
        val isFinanciallyImpacted = status == OrderStatus.ON_THE_WAY || status == OrderStatus.DELIVERED

        if (isFinanciallyImpacted && !wasFinanciallyImpacted) {
            val restaurant = order.restaurant
            restaurant.balance = restaurant.balance.add(java.math.BigDecimal.valueOf(order.deliveryFee))
            restaurantRepository.save(restaurant)
        } else if (!isFinanciallyImpacted && wasFinanciallyImpacted) {
            val restaurant = order.restaurant
            restaurant.balance = restaurant.balance.subtract(java.math.BigDecimal.valueOf(order.deliveryFee))
            restaurantRepository.save(restaurant)
        }

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

        // Free up driver
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) {
            order.driver?.let { profile ->
                profile.status = DriverStatus.IDLE
                driverProfileRepository.save(profile)
            }
        }

        // ON_THE_WAY → simulate route to customer
        if (status == OrderStatus.ON_THE_WAY && order.driver != null) {
            val customer = order.customer
            // Use pinned coordinates if available, otherwise fall back to zone centroid
            val endLat = customer.latitude
                ?: customer.deliveryZone?.polygon?.centroid?.y
            val endLng = customer.longitude
                ?: customer.deliveryZone?.polygon?.centroid?.x

            if (endLat != null && endLng != null && order.driver!!.id != null) {
                simulationService.simulateDriverMovement(
                    orderId = order.id,
                    driverId = order.driver!!.id!!,
                    startLat = order.restaurant.location.y,
                    startLng = order.restaurant.location.x,
                    endLat = endLat,
                    endLng = endLng,
                    durationSeconds = 120
                )
            }
        }

        return mapToResponse(orderRepository.save(order))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Active delivery query
    // ─────────────────────────────────────────────────────────────────────────

    fun getActiveDeliveryForDriver(driverId: java.util.UUID): OrderResponse? {
        val targetStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.ON_THE_WAY)
        val orders = orderRepository.findByDriverIdAndStatusInOrderByCreatedAtDesc(driverId, targetStatuses)
        return orders.firstOrNull()?.let { mapToResponse(it) }
    }

    @Transactional
    fun assignDriverFromScheduler(orderId: Long, driverProfileId: java.util.UUID): Boolean {
        val order = orderRepository.findById(orderId).orElse(null) ?: return false

        if (order.driver != null) return false

        val driverProfile = driverProfileRepository.findById(driverProfileId).orElse(null) ?: return false

        order.driver = driverProfile
        orderRepository.save(order)

        driverProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(driverProfile)

        notificationService.notifyFrontend(order.id, order.restaurant.owner.phoneNumber)
        notificationService.notifyDriver(order.id, driverProfile.user.phoneNumber)

        startSimulationToRestaurant(order, driverProfile)

        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun startSimulationToRestaurant(order: Order, driverProfile: DriverProfile) {
        if (driverProfile.currentLocation != null && driverProfile.id != null) {
            simulationService.simulateDriverMovement(
                orderId = order.id,
                driverId = driverProfile.id!!,
                startLat = driverProfile.currentLocation!!.y,
                startLng = driverProfile.currentLocation!!.x,
                endLat = order.restaurant.location.y,
                endLng = order.restaurant.location.x,
                durationSeconds = 120
            )
        }
    }

    private fun mapToResponse(order: Order): OrderResponse {
        val customer = order.customer
        // Effective coordinates: exact pin takes priority over zone centroid
        val effectiveLat = customer.latitude ?: customer.deliveryZone?.polygon?.centroid?.y
        val effectiveLng = customer.longitude ?: customer.deliveryZone?.polygon?.centroid?.x

        return OrderResponse(
            id = order.id,
            restaurantId = order.restaurant.id,
            restaurantName = order.restaurant.name,
            driverPhoneNumber = order.driver?.user?.phoneNumber,
            driverFullName = order.driver?.user?.fullName,
            status = order.status,
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
            customerId = customer.id,
            customerName = customer.fullName,
            customerPhone = customer.phoneNumber,
            deliveryZoneId = customer.deliveryZone?.id,
            deliveryZoneName = customer.deliveryZone?.name,
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
            locationMethod = if (customer.deliveryZone != null) "DELIVERY_ZONE" else "WHATSAPP_LINK"
        )
    }
}
