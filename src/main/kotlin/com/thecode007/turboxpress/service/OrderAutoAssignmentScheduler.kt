package com.thecode007.turboxpress.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.thecode007.turboxpress.entity.OrderStatus
import com.thecode007.turboxpress.repository.DriverProfileRepository
import com.thecode007.turboxpress.repository.OrderRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class OrderAutoAssignmentScheduler(
    private val orderRepository: OrderRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val orderService: OrderService
) {

    @Scheduled(fixedDelay = 15000)
    fun assignDriversToAcceptedOrders() {
        val targetStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP)
        val unassignedOrders = orderRepository.findByDriverIsNullAndStatusInOrderByCreatedAtAsc(targetStatuses)

        if (unassignedOrders.isEmpty()) {
            return
        }

        for (order in unassignedOrders) {
            val nearestProfile = driverProfileRepository.findNearestIdleDriver(order.restaurant.location)
            
            if (nearestProfile != null) {
                // Assign driver
                val assigned = orderService.assignDriverFromScheduler(order.id, nearestProfile)
                
                if (assigned) {
                    // Notify the owner explicitly that a driver has been assigned to their order.
                    val ownerPhone = order.restaurant.owner.phoneNumber
                    notifyFrontend(order.id, ownerPhone)
                }
            }
        }
    }

    private fun notifyFrontend(orderId: Long?, ownerPhoneNumber: String?) {
        if (orderId == null || ownerPhoneNumber == null) return
        try {
            val sanitizedPhone = ownerPhoneNumber.replace(Regex("[^a-zA-Z0-9-_.~%]"), "")
            val topic = "owner_$sanitizedPhone"
            val message = Message.builder()
                .setTopic(topic)
                .setNotification(
                    Notification.builder()
                        .setTitle("Driver Assigned")
                        .setBody("A driver has been successfully assigned to order #$orderId.")
                        .build()
                )
                .putData("orderId", orderId.toString())
                .putData("type", "DRIVER_ASSIGNED")
                .build()

            FirebaseMessaging.getInstance().send(message)
        } catch (e: Exception) {
            println("Failed to send driver assignment notification for order $orderId: ${e.message}")
        }
    }
}
