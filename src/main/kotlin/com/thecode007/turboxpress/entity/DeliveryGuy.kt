package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import java.time.Instant
import java.math.BigDecimal

@Entity
@Table(name = "delivery_guys")
data class DeliveryGuy(
    @Id
    @Column(name = "phone_number", length = 20)
    val phoneNumber: String,
    
    @Column(nullable = false, unique = true, length = 50)
    var username: String,
    
    @Column(name = "full_name", nullable = false)
    var fullName: String,
    
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,
    
    @Column(name = "profile_picture_url", length = 500)
    var profilePictureUrl: String? = null,
    
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    
    @Column(name = "monthly_sub_fee", nullable = false)
    var monthlySubFee: Double = 0.0,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    var billingCycle: BillingCycle = BillingCycle.MONTHLY,
    
    @Column(name = "next_billing_date", nullable = false)
    var nextBillingDate: java.time.LocalDate = java.time.LocalDate.now().plusMonths(1),
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(name = "carried_over_balance", nullable = false)
    var carriedOverBalance: Double = 0.0,

    @Column(name = "admin_debt_balance", nullable = false, precision = 19, scale = 4)
    var adminDebtBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "collected_cash_balance", nullable = false, precision = 19, scale = 4)
    var collectedCashBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "daily_rate", nullable = false, precision = 19, scale = 4)
    var dailyRate: BigDecimal = BigDecimal.ZERO
)

enum class BillingCycle {
    DAILY,
    WEEKLY,
    MONTHLY
}
