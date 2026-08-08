package com.thecode007.turboxpress.service

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.thecode007.turboxpress.repository.UserRepository
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class NotificationService(
    private val userRepository: UserRepository,
    private val messageSource: MessageSource
) {

    private fun getUserLocale(phoneNumber: String): Locale {
        val user = userRepository.findByPhoneNumber(phoneNumber).orElse(null)
        val lang = user?.preferredLanguage ?: "en"
        return Locale.forLanguageTag(lang)
    }

    private fun getLocalizedMessage(key: String, locale: Locale, vararg args: Any): String {
        return try {
            messageSource.getMessage(key, args, locale)
        } catch (e: Exception) {
            key
        }
    }

    fun notifyFrontend(orderId: Long?, ownerPhoneNumber: String?) {
        if (orderId == null || ownerPhoneNumber == null) return
        try {
            val sanitizedPhone = ownerPhoneNumber.replace(Regex("[^a-zA-Z0-9-_.~%]"), "")
            val topic = "owner_$sanitizedPhone"
            val locale = getUserLocale(ownerPhoneNumber)
            val title = getLocalizedMessage("notification.DRIVER_ASSIGNED.title", locale)
            val body = getLocalizedMessage("notification.DRIVER_ASSIGNED.body", locale, orderId.toString())

            val androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build()
            val apnsConfig = ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .setAps(Aps.builder().setContentAvailable(true).build())
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
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
            val locale = getUserLocale(ownerPhoneNumber)
            val title = getLocalizedMessage("notification.DRIVER_REJECTED.title", locale)
            val body = getLocalizedMessage("notification.DRIVER_REJECTED.body", locale, orderId.toString(), driverName ?: "Unknown")

            val androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build()
            val apnsConfig = ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .setAps(Aps.builder().setContentAvailable(true).build())
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
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
            val locale = getUserLocale(driverPhoneNumber)
            val title = getLocalizedMessage("notification.NEW_DELIVERY_REQUEST.title", locale)
            val body = getLocalizedMessage("notification.NEW_DELIVERY_REQUEST.body", locale, orderId.toString())

            val androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build()
            val apnsConfig = ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .setAps(Aps.builder().setContentAvailable(true).build())
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
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
            val locale = getUserLocale(driverPhoneNumber)
            val title = getLocalizedMessage("notification.ORDER_READY.title", locale)
            val body = getLocalizedMessage("notification.ORDER_READY.body", locale, orderId.toString())

            val androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build()
            val apnsConfig = ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .setAps(Aps.builder().setContentAvailable(true).build())
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
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
            val locale = getUserLocale(driverPhoneNumber)
            val title = getLocalizedMessage("notification.DRIVER_UNASSIGNED.title", locale)
            val body = getLocalizedMessage("notification.DRIVER_UNASSIGNED.body", locale, orderId.toString())

            val androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build()
            val apnsConfig = ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .setAps(Aps.builder().setContentAvailable(true).build())
                .build()

            val message = Message.builder()
                .setTopic(topic)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
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
                val locale = getUserLocale(phone)
                val title = getLocalizedMessage("notification.BROADCAST_ORDER.title", locale)
                val body = getLocalizedMessage("notification.BROADCAST_ORDER.body", locale, orderId.toString())

                val androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build()
                val apnsConfig = ApnsConfig.builder()
                    .putHeader("apns-priority", "10")
                    .setAps(Aps.builder().setContentAvailable(true).build())
                    .build()

                Message.builder()
                    .setTopic(topic)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .setAndroidConfig(androidConfig)
                    .setApnsConfig(apnsConfig)
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
                val locale = getUserLocale(phone)
                val title = getLocalizedMessage("notification.BROADCAST_ORDER_TAKEN.title", locale)
                val body = getLocalizedMessage("notification.BROADCAST_ORDER_TAKEN.body", locale, orderId.toString())
                
                // Keep data-only if preferred, but for consistency we add notification.
                // The mobile app can choose to suppress it or we just let it show.
                // The previous code had a comment: "Data-only so onMessageReceived ALWAYS fires".
                // Adding notification payload might change behavior on Android when in background.
                // However, since we are doing proper notifications, let's keep it here.
                val androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build()
                val apnsConfig = ApnsConfig.builder()
                    .putHeader("apns-priority", "10")
                    .setAps(Aps.builder().setContentAvailable(true).build())
                    .build()

                Message.builder()
                    .setTopic(topic)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .setAndroidConfig(androidConfig)
                    .setApnsConfig(apnsConfig)
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
