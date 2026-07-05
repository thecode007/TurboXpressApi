package com.thecode007.turboxpress.config

import com.thecode007.turboxpress.controller.DriverLocationWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val driverLocationWebSocketHandler: DriverLocationWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(driverLocationWebSocketHandler, "/ws/driver-location")
            .setAllowedOrigins("*") // Allow all origins for testing/local development
    }
}
