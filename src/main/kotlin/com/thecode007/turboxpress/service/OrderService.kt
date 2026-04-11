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
    private val deliveryGuyRepository: DeliveryGuyRepository
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
            .orElseThrow { ResourceNotFoundException("Driver not found with phone number: $driverPhoneNumber") }

        order.driver = driver
        order.status = OrderStatus.ACCEPTED
        return mapToResponse(orderRepository.save(order))
    }

    @Transactional
    fun updateOrderStatus(orderId: Long, status: OrderStatus): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ResourceNotFoundException("Order not found with id: $orderId") }
        
        order.status = status
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
