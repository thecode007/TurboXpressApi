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
    private val notificationService: NotificationService
) {

    @Transactional
    fun createOrder(request: OrderCreateRequest): OrderResponse {
        val restaurant = restaurantRepository.findById(request.restaurantId)
            .orElseThrow { ResourceNotFoundException("Restaurant not found with id: ${request.restaurantId}") }

        val order = Order(
            restaurant = restaurant,
            totalAmount = 0.0,
            customerName = request.customerName,
            customerPhone = request.customerPhone,
            locationMethod = request.locationMethod,
            deliveryZoneId = request.deliveryZoneId,
            whatsappMapLink = request.whatsappMapLink,
            detailedAddress = request.detailedAddress,
            latitude = request.latitude,
            longitude = request.longitude,
            routeDistanceKm = request.routeDistanceKm,
            deliveryFee = request.deliveryFee
        )

        val savedOrder = orderRepository.save(order)
        var totalAmount = 0.0

        val orderItems = request.items.map { itemRequest ->
            val menuItem = restaurantItemRepository.findById(itemRequest.menuItemId)
                .orElseThrow { ResourceNotFoundException("Menu item not found with id: ${itemRequest.menuItemId}") }
            
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
        savedOrder.items = orderItems.toMutableList()
        val finalOrder = orderRepository.save(savedOrder)

        return mapToResponse(finalOrder)
    }

    fun getOrderById(id: Long): OrderResponse {
        val order = orderRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Order not found with id: $id") }
        return mapToResponse(order)
    }

    fun getAllOrders(): List<OrderResponse> {
        val twentyFourHoursAgo = java.time.Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS)
        return orderRepository.findByCreatedAtAfterOrderByCreatedAtDesc(twentyFourHoursAgo).map { mapToResponse(it) }
    }

    @Transactional
    fun assignDriver(orderId: Long, driverPhoneNumber: String): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found with id: $orderId") }
        
        val user = userRepository.findByPhoneNumber(driverPhoneNumber)
            .orElseThrow { ResourceNotFoundException("User not found with phone: $driverPhoneNumber") }
        val driverProfile = user.id?.let { driverProfileRepository.findById(it).orElse(null) }
            ?: throw ResourceNotFoundException("Driver profile not found with phone: $driverPhoneNumber")

        order.driver = driverProfile
        order.status = OrderStatus.ACCEPTED
        
        driverProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(driverProfile)
        
        val savedOrder = orderRepository.save(order)
        
        // Notify owner and driver
        notificationService.notifyFrontend(savedOrder.id, savedOrder.restaurant.owner.phoneNumber)
        notificationService.notifyDriver(savedOrder.id, driverPhoneNumber)
        
        return mapToResponse(savedOrder)
    }

    @Transactional
    fun autoAssignDriver(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found with id: $orderId") }
        
        val nearestProfile = driverProfileRepository.findNearestIdleDriver(order.restaurant.location)
            ?: throw ResourceNotFoundException("No idle drivers available near the restaurant")

        order.driver = nearestProfile
        order.status = OrderStatus.ACCEPTED
        nearestProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(nearestProfile)
        
        val savedOrder = orderRepository.save(order)
        
        // Notify owner and driver
        notificationService.notifyFrontend(savedOrder.id, savedOrder.restaurant.owner.phoneNumber)
        notificationService.notifyDriver(savedOrder.id, nearestProfile.user.phoneNumber)
        
        return mapToResponse(savedOrder)
    }

    @Transactional
    fun rejectOrder(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found with id: $orderId") }
        
        val driverProfile = order.driver 
            ?: throw IllegalStateException("Order is not assigned to a driver")

        // Unassign driver so it can be picked up by the auto-assignment scheduler
        order.driver = null
        
        driverProfile.onlineStatus = OnlineStatus.OFFLINE
        driverProfile.status = DriverStatus.IDLE
        driverProfileRepository.save(driverProfile)
        
        return mapToResponse(orderRepository.save(order))
    }

    @Transactional
    fun updateOrderStatus(orderId: Long, status: OrderStatus): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found with id: $orderId") }
        
        val oldStatus = order.status
        order.status = status
        
        val wasFinanciallyImpacted = oldStatus == OrderStatus.ON_THE_WAY || oldStatus == OrderStatus.DELIVERED
        val isFinanciallyImpacted = status == OrderStatus.ON_THE_WAY || status == OrderStatus.DELIVERED
        
        if (isFinanciallyImpacted && !wasFinanciallyImpacted) {
            val restaurant = order.restaurant
            val debtAmount = order.deliveryFee
            restaurant.balance = restaurant.balance.add(java.math.BigDecimal.valueOf(debtAmount))
            restaurantRepository.save(restaurant)
        } else if (!isFinanciallyImpacted && wasFinanciallyImpacted) {
            val restaurant = order.restaurant
            val debtAmount = order.deliveryFee
            restaurant.balance = restaurant.balance.subtract(java.math.BigDecimal.valueOf(debtAmount))
            restaurantRepository.save(restaurant)
        }
        
        // If order is completed, cancelled, or rejected, free up the driver
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) {
            order.driver?.let { profile ->
                profile.status = DriverStatus.IDLE
                driverProfileRepository.save(profile)
            }
        }
        
        return mapToResponse(orderRepository.save(order))
    }

    fun getActiveDeliveryForDriver(driverId: java.util.UUID): OrderResponse? {
        val targetStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.ON_THE_WAY)
        val orders = orderRepository.findByDriverIdAndStatusInOrderByCreatedAtDesc(driverId, targetStatuses)
        return orders.firstOrNull()?.let { mapToResponse(it) }
    }

    @Transactional
    fun assignDriverFromScheduler(orderId: Long, driverProfileId: java.util.UUID): Boolean {
        val order = orderRepository.findById(orderId).orElse(null) ?: return false
        
        if (order.driver != null) {
            return false
        }
        
        val driverProfile = driverProfileRepository.findById(driverProfileId).orElse(null) ?: return false
        
        order.driver = driverProfile
        // Status remains unchanged (already ACCEPTED or PREPARING)
        orderRepository.save(order)
        
        driverProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(driverProfile)
        
        // Notify owner and driver
        notificationService.notifyFrontend(order.id, order.restaurant.owner.phoneNumber)
        notificationService.notifyDriver(order.id, driverProfile.user.phoneNumber)
        
        return true
    }

    private fun mapToResponse(order: Order): OrderResponse {
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
            customerName = order.customerName,
            customerPhone = order.customerPhone,
            locationMethod = order.locationMethod,
            deliveryZoneId = order.deliveryZoneId,
            whatsappMapLink = order.whatsappMapLink,
            detailedAddress = order.detailedAddress,
            latitude = order.latitude,
            longitude = order.longitude,
            driverLat = order.driver?.currentLocation?.y,
            driverLng = order.driver?.currentLocation?.x,
            routeDistanceKm = order.routeDistanceKm,
            deliveryFee = order.deliveryFee,
            createdAt = order.createdAt
        )
    }
}
