package com.thecode007.turboxpress.service

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.springframework.stereotype.Service

@Service
class NotificationService {

    fun notifyFrontend(orderId: Long?, ownerPhoneNumber: String?) {
        if (orderId == null || ownerPhoneNumber == null) return
        try {
            val sanitizedPhone = ownerPhoneNumber.replace(Regex("[^a-zA-Z0-9-_.~%]"), "")
            val topic = "owner_$sanitizedPhone"
            val androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder().setChannelId("turboxpress_high_priority").build())
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setAndroidConfig(androidConfig)
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

    fun notifyDriver(orderId: Long?, driverPhoneNumber: String?) {
        if (orderId == null || driverPhoneNumber == null) return
        try {
            val sanitizedPhone = driverPhoneNumber.replace(Regex("[^a-zA-Z0-9-_.~%]"), "")
            val topic = "driver_$sanitizedPhone"
            val androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder().setChannelId("turboxpress_high_priority").build())
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setAndroidConfig(androidConfig)
                .setNotification(
                    Notification.builder()
                        .setTitle("New Delivery Request")
                        .setBody("You have a new delivery request for order #$orderId.")
                        .build()
                )
                .putData("orderId", orderId.toString())
                .putData("type", "NEW_DELIVERY_REQUEST")
                .build()

            FirebaseMessaging.getInstance().send(message)
        } catch (e: Exception) {
            println("Failed to send delivery request notification for driver $driverPhoneNumber: ${e.message}")
        }
    }
}
