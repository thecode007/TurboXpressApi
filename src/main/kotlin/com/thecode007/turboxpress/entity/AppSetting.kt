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
    var driverSubscriptionFee: Double = 0.0
)
