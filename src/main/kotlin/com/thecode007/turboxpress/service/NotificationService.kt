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

    fun notifyOwnerDriverRejected(orderId: Long?, ownerPhoneNumber: String?, driverName: String?) {
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
                        .setTitle("Order Rejected")
                        .setBody("Driver ${driverName ?: "Unknown"} has rejected order #$orderId.")
                        .build()
                )
                .putData("orderId", orderId.toString())
                .putData("type", "DRIVER_REJECTED")
                .build()

            FirebaseMessaging.getInstance().send(message)
        } catch (e: Exception) {
            println("Failed to send driver rejection notification for order $orderId: ${e.message}")
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
    fun notifyDriverOrderReady(orderId: Long?, driverPhoneNumber: String?) {
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
                        .setTitle("Order Ready for Pickup")
                        .setBody("Order #$orderId is ready for pickup at the restaurant.")
                        .build()
                )
                .putData("orderId", orderId.toString())
                .putData("type", "ORDER_READY")
                .build()

            FirebaseMessaging.getInstance().send(message)
        } catch (e: Exception) {
            println("Failed to send order ready notification for driver $driverPhoneNumber: ${e.message}")
        }
    }

    fun notifyDriverUnassigned(orderId: Long?, driverPhoneNumber: String?) {
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
                        .setTitle("Order Unassigned")
                        .setBody("You have been unassigned from order #$orderId.")
                        .build()
                )
                .putData("orderId", orderId.toString())
                .putData("type", "DRIVER_UNASSIGNED")
                .build()

            FirebaseMessaging.getInstance().send(message)
        } catch (e: Exception) {
            println("Failed to send driver unassigned notification for driver $driverPhoneNumber: ${e.message}")
        }
    }

    fun broadcastOrderToDrivers(orderId: Long?, driverPhoneNumbers: List<String>) {
        if (orderId == null || driverPhoneNumbers.isEmpty()) return
        try {
            val messages = driverPhoneNumbers.map { phone ->
                val sanitizedPhone = phone.replace(Regex("[^a-zA-Z0-9-_.~%]"), "")
                val topic = "driver_$sanitizedPhone"
                val androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder().setChannelId("turboxpress_high_priority").build())
                    .build()

                Message.builder()
                    .setTopic(topic)
                    .setAndroidConfig(androidConfig)
                    .setNotification(
                        Notification.builder()
                            .setTitle("New Order Available!")
                            .setBody("Order #$orderId is available. Tap to accept.")
                            .build()
                    )
                    .putData("orderId", orderId.toString())
                    .putData("type", "BROADCAST_ORDER")
                    .build()
            }
            // Send individually. FCM also supports sendAll for batch sending.
            // Using sendAll for efficiency if there are many drivers.
            FirebaseMessaging.getInstance().sendAll(messages)
            println("Broadcasted order #$orderId to ${messages.size} drivers")
        } catch (e: Exception) {
            println("Failed to broadcast order $orderId: ${e.message}")
        }
    }

    fun notifyBroadcastOrderTaken(orderId: Long, excludePhone: String, otherDriverPhones: List<String>) {
        val targets = otherDriverPhones.filter { it != excludePhone }
        if (targets.isEmpty()) return
        try {
            val messages = targets.map { phone ->
                val sanitizedPhone = phone.replace(Regex("[^a-zA-Z0-9-_.~%]"), "")
                val topic = "driver_$sanitizedPhone"
                // Data-only (no notification payload) so onMessageReceived ALWAYS fires
                // even when the app is in the background on Android.
                val androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build()

                Message.builder()
                    .setTopic(topic)
                    .setAndroidConfig(androidConfig)
                    .putData("orderId", orderId.toString())
                    .putData("type", "BROADCAST_ORDER_TAKEN")
                    .build()
            }
            FirebaseMessaging.getInstance().sendAll(messages)
            println("Notified ${messages.size} drivers that order #$orderId was taken")
        } catch (e: Exception) {
            println("Failed to notify drivers of taken order $orderId: ${e.message}")
        }
    }
}
