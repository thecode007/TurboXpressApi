package com.thecode007.turboxpress.service

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
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
    private val orderService: OrderService,
    private val notificationService: NotificationService,
    private val appSettingService: AppSettingService
) {

    @Scheduled(fixedDelay = 15000)
    fun assignDriversToAcceptedOrders() {
        if (!appSettingService.getSettings().isAutoAssignEnabled) {
            return
        }

        val targetStatuses = listOf(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP)
        val unassignedOrders = orderRepository.findByDriverIsNullAndStatusInOrderByCreatedAtAsc(targetStatuses)

        if (unassignedOrders.isEmpty()) {
            return
        }

        for (order in unassignedOrders) {
            val nearestProfile = driverProfileRepository.findNearestIdleDriver(order.restaurant.location)
            
            if (nearestProfile != null) {
                // Assign driver (this now also handles notifications internally)
                orderService.autoAssignDriver(order.id)
            }
        }
    }

}
