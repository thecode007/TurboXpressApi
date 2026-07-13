package com.thecode007.turboxpress.entity

import jakarta.persistence.*

@Entity
@Table(name = "app_settings")
data class AppSetting(
    @Id
    val id: Long = 1L,

    @Column(nullable = false)
    var deliveryProfitPercent: Double = 0.0,

    @Column(nullable = false)
    var restaurantSubscriptionFee: Double = 0.0,

    @Column(nullable = false)
    var driverSubscriptionFee: Double = 0.0,

    @Column(nullable = false)
    var pricePerKm: Double = 1.5, // Default value

    @Column(name = "base_fare", nullable = false, columnDefinition = "float8 default 0.0")
    var baseFare: Double = 0.0,

    @Column(name = "is_auto_assign_enabled", nullable = false, columnDefinition = "boolean default true")
    var isAutoAssignEnabled: Boolean = true
)
