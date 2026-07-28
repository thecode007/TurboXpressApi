package com.thecode007.turboxpress.service

import com.google.firebase.cloud.FirestoreClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Broadcasts order lifecycle events to the desktop admin app via **Cloud Firestore**.
 *
 * Instead of holding open SSE HTTP connections (which exhaust server threads), each event
 * is written to a single well-known Firestore document `order_events/latest`.  The desktop
 * admin app subscribes to that document with a real-time snapshot listener — Firestore's
 * own long-polling infrastructure handles the delivery with no extra server resources.
 *
 * Events flow: OrderService → OrderEventBroadcaster → Firestore `order_events/latest` → Desktop client
 */
@Service
class OrderEventBroadcaster {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Write an order event to Firestore so all connected desktop supervisors receive it
     * in real-time via their snapshot listeners.
     *
     * @param type           Event type: ORDER_CREATED | ORDER_STATUS_CHANGED | DRIVER_ASSIGNED | ORDER_UPDATED
     * @param orderId        The affected order ID
     * @param status         The new order status (nullable for non-status events)
     * @param customerName   Customer's name for display in notification
     * @param restaurantName Restaurant name for display in notification
     * @param driverName     Assigned driver name (for DRIVER_ASSIGNED events)
     */
    fun broadcast(
        type: String,
        orderId: Long,
        status: String? = null,
        customerName: String? = null,
        restaurantName: String? = null,
        driverName: String? = null
    ) {
        try {
            val firestore = FirestoreClient.getFirestore()
            val data = mutableMapOf<String, Any>(
                "type" to type,
                "orderId" to orderId,
                "timestamp" to System.currentTimeMillis()
            )
            status?.let { data["status"] = it }
            customerName?.let { data["customerName"] = it }
            restaurantName?.let { data["restaurantName"] = it }
            driverName?.let { data["driverName"] = it }

            firestore.collection("order_events").document("latest").set(data)
            logger.info("Firestore order event written: type=$type, orderId=$orderId")
        } catch (e: Exception) {
            logger.error("Failed to write order event to Firestore: ${e.message}", e)
        }
    }
}
