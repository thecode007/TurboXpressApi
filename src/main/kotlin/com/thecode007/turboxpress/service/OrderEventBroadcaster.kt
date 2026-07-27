package com.thecode007.turboxpress.service

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Singleton service that holds all active SSE emitters (one per connected desktop client)
 * and broadcasts order lifecycle events to them in real-time.
 *
 * Events flow: OrderService → OrderEventBroadcaster → SseEmitter → Desktop client
 */
@Service
class OrderEventBroadcaster {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    /** Thread-safe list of currently connected emitters. */
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    /**
     * Register a new SSE emitter for an incoming desktop client connection.
     * Timeout is set to 0 (infinite) — the client will reconnect on drop.
     * Immediately sends a SYNC event with the current active orders for state reconciliation.
     */
    fun register(initialOrders: List<com.thecode007.turboxpress.dto.OrderResponse>): SseEmitter {
        val emitter = SseEmitter(0L) // 0 = no server-side timeout

        emitters.add(emitter)
        logger.info("SSE client connected. Total active: ${emitters.size}")

        // Send the SYNC event asynchronously so the controller can return the emitter
        Thread {
            try {
                // Give Spring Web MVC a tiny moment to commit the response and establish the stream
                Thread.sleep(200)
                
                val payload = mapOf(
                    "type" to "SYNC",
                    "orders" to initialOrders,
                    "timestamp" to System.currentTimeMillis()
                )
                emitter.send(
                    SseEmitter.event()
                        .name("order-event")
                        .data(objectMapper.writeValueAsString(payload))
                )
            } catch (e: Exception) {
                logger.warn("Failed to send SYNC event: ${e.message}")
            }
        }.start()

        // Clean up on completion, timeout, or error
        val cleanup = Runnable {
            emitters.remove(emitter)
            logger.info("SSE client disconnected. Total active: ${emitters.size}")
        }
        emitter.onCompletion(cleanup)
        emitter.onTimeout(cleanup)
        emitter.onError { cleanup.run() }

        return emitter
    }

    /**
     * Broadcast an order event to ALL connected desktop supervisors.
     *
     * @param type    Event type: ORDER_CREATED | ORDER_STATUS_CHANGED | DRIVER_ASSIGNED | ORDER_UPDATED
     * @param orderId The affected order ID
     * @param status  The new order status (nullable for non-status events)
     * @param customerName Customer's name for display in notification
     * @param restaurantName Restaurant name for display in notification
     * @param driverName  Assigned driver name (for DRIVER_ASSIGNED events)
     */
    fun broadcast(
        type: String,
        orderId: Long,
        status: String? = null,
        customerName: String? = null,
        restaurantName: String? = null,
        driverName: String? = null
    ) {
        if (emitters.isEmpty()) return

        val payload = buildMap<String, Any?> {
            put("type", type)
            put("orderId", orderId)
            if (status != null) put("status", status)
            if (customerName != null) put("customerName", customerName)
            if (restaurantName != null) put("restaurantName", restaurantName)
            if (driverName != null) put("driverName", driverName)
            put("timestamp", System.currentTimeMillis())
        }

        val json = objectMapper.writeValueAsString(payload)
        val deadEmitters = mutableListOf<SseEmitter>()

        emitters.forEach { emitter ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("order-event")
                        .data(json)
                )
            } catch (e: Exception) {
                logger.warn("Failed to send SSE event to client, marking for removal: ${e.message}")
                deadEmitters.add(emitter)
            }
        }

        // Remove dead emitters discovered during broadcast
        if (deadEmitters.isNotEmpty()) {
            emitters.removeAll(deadEmitters)
            logger.info("Removed ${deadEmitters.size} dead SSE emitter(s). Active: ${emitters.size}")
        }
    }
}
