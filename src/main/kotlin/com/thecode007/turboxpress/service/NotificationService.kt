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
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setAndroidConfig(androidConfig)
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
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setAndroidConfig(androidConfig)
                .putData("orderId", orderId.toString())
                .putData("type", "DRIVER_REJECTED")
                .putData("driverName", driverName ?: "Unknown")
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
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setAndroidConfig(androidConfig)
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
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setAndroidConfig(androidConfig)
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
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setAndroidConfig(androidConfig)
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
                    .build()

                Message.builder()
                    .setTopic(topic)
                    .setAndroidConfig(androidConfig)
                    .putData("orderId", orderId.toString())
                    .putData("type", "BROADCAST_ORDER")
                    .build()
            }
            val batchResponse = FirebaseMessaging.getInstance().sendEach(messages)
            val failed = batchResponse.responses.filter { !it.isSuccessful }
            if (failed.isEmpty()) {
                println("Broadcasted order #$orderId to ${messages.size} driver(s) successfully.")
            } else {
                println("Broadcasted order #$orderId: ${batchResponse.successCount} succeeded, ${failed.size} failed.")
                failed.forEach { println("  FCM send failure: ${it.exception?.message}") }
            }
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
            val batchResponse = FirebaseMessaging.getInstance().sendEach(messages)
            val failed = batchResponse.responses.filter { !it.isSuccessful }
            if (failed.isEmpty()) {
                println("Notified ${messages.size} driver(s) that order #$orderId was taken.")
            } else {
                println("Order taken notify: ${batchResponse.successCount} succeeded, ${failed.size} failed.")
                failed.forEach { println("  FCM send failure: ${it.exception?.message}") }
            }
        } catch (e: Exception) {
            println("Failed to notify drivers of taken order $orderId: ${e.message}")
        }
    }
}
