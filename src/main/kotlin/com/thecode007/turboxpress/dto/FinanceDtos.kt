package com.thecode007.turboxpress.dto

import com.thecode007.turboxpress.entity.OrderStatus
import java.time.Instant
import java.time.LocalDate

data class RestaurantFinanceSummary(
    val restaurantId: Long,
    val restaurantName: String,
    val grossSales: Double,
    val commissionsOwed: Double,
    val subFeeOwed: Double,
    val balance: Double,
    val carriedOverBalance: Double,
    val totalBalanceDue: Double,
    val nextBillingDate: LocalDate,
    val recentOrders: List<OrderFinanceItem>
)

data class OrderFinanceItem(
    val id: Long,
    val createdAt: Instant,
    val totalAmount: Double,
    val platformCommissionAmount: Double,
    val status: OrderStatus,
    val isSettled: Boolean
)

data class DriverFinanceSummary(
    val phoneNumber: String,
    val fullName: String,
    val deliveryFeesOwed: Double,
    val subFeeOwed: Double,
    val adminDebtBalance: Double,
    val collectedCashBalance: Double,
    val dailyRate: Double,
    val carriedOverBalance: Double,
    val totalBalanceDue: Double,
    val nextBillingDate: LocalDate,
    val recentOrders: List<DriverOrderFinanceItem>
)

data class DriverOrderFinanceItem(
    val id: Long,
    val createdAt: Instant,
    val totalAmount: Double,
    val deliveryFee: Double,
    val status: OrderStatus,
    val isSettledDriver: Boolean
)
