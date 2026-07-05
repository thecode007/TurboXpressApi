package com.thecode007.turboxpress.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CopyOnWriteArrayList

@Component
class DriverLocationWebSocketHandler : TextWebSocketHandler() {

    private val objectMapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(javaClass)
    private val sessions = CopyOnWriteArrayList<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
        logger.info("WebSocket connected: ${session.id}")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
        logger.info("WebSocket disconnected: ${session.id}")
    }

    fun broadcastLocation(driverId: String, latitude: Double, longitude: Double) {
        val payload = mapOf(
            "driverId" to driverId,
            "latitude" to latitude,
            "longitude" to longitude
        )
        val message = TextMessage(objectMapper.writeValueAsString(payload))
        
        sessions.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(message)
                } catch (e: Exception) {
                    logger.error("Error sending WebSocket message to session \${session.id}", e)
                }
            }
        }
    }
}
