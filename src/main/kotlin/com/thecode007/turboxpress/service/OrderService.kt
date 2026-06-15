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
    private val deliveryGuyRepository: DeliveryGuyRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun createOrder(request: OrderCreateRequest): OrderResponse {
        val restaurant = restaurantRepository.findById(request.restaurantId)
            .orElseThrow { ResourceNotFoundException("Restaurant not found with id: ${request.restaurantId}") }

        val order = Order(
            restaurant = restaurant,
            totalAmount = 0.0
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
        return orderRepository.findAll().map { mapToResponse(it) }
    }

    @Transactional
    fun assignDriver(orderId: Long, driverPhoneNumber: String): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found with id: $orderId") }
        
        val driver = deliveryGuyRepository.findById(driverPhoneNumber)
            .orElseThrow { ResourceNotFoundException("Driver not found with phone: $driverPhoneNumber") }

        order.driver = driver
        order.status = OrderStatus.ACCEPTED
        
        val userOpt = userRepository.findByPhoneNumber(driverPhoneNumber)
        userOpt.ifPresent { user ->
            user.id?.let { id ->
                driverProfileRepository.findById(id).ifPresent { profile ->
                    profile.status = DriverStatus.ON_DELIVERY
                    driverProfileRepository.save(profile)
                }
            }
        }
        
        return mapToResponse(orderRepository.save(order))
    }

    @Transactional
    fun autoAssignDriver(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found with id: $orderId") }
        
        val nearestProfile = driverProfileRepository.findNearestIdleDriver(order.restaurant.location)
            ?: throw ResourceNotFoundException("No idle drivers available near the restaurant")

        val driverPhoneNumber = nearestProfile.user.phoneNumber
        val driver = deliveryGuyRepository.findById(driverPhoneNumber)
            .orElseThrow { ResourceNotFoundException("Delivery guy not found for phone number: $driverPhoneNumber") }

        order.driver = driver
        order.status = OrderStatus.ACCEPTED
        nearestProfile.status = DriverStatus.ON_DELIVERY
        driverProfileRepository.save(nearestProfile)
        
        return mapToResponse(orderRepository.save(order))
    }

    @Transactional
    fun updateOrderStatus(orderId: Long, status: OrderStatus): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found with id: $orderId") }
        
        order.status = status
        
        // If order is completed or cancelled, free up the driver
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED) {
            order.driver?.let { driver ->
                val userOpt = userRepository.findByPhoneNumber(driver.phoneNumber)
                userOpt.ifPresent { user ->
                    user.id?.let { id ->
                        driverProfileRepository.findById(id).ifPresent { profile ->
                            profile.status = DriverStatus.IDLE
                            driverProfileRepository.save(profile)
                        }
                    }
                }
            }
        }
        
        return mapToResponse(orderRepository.save(order))
    }

    private fun mapToResponse(order: Order): OrderResponse {
        return OrderResponse(
            id = order.id,
            restaurantId = order.restaurant.id,
            restaurantName = order.restaurant.name,
            driverPhoneNumber = order.driver?.phoneNumber,
            driverFullName = order.driver?.fullName,
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
            createdAt = order.createdAt
        )
    }
}
