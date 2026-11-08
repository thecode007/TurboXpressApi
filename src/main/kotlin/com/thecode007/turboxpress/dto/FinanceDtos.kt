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
    val period: String = "DAILY",
    val selectedDate: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val previousDate: String? = null,
    val nextDate: String? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val periodOrderCount: Int = 0,
    val periodUnsettledOrderCount: Int = 0,
    val periodTotalDeliveryFees: Double = 0.0,
    val periodTotalProfit: Double = 0.0,
    val periodDeliveryFeesOwed: Double = 0.0,
    val periodProfitToCollect: Double = 0.0,
    val periodSettledProfit: Double = 0.0,
    val isPeriodSettled: Boolean = true,
    val deliveryFeesOwed: Double,
    val deliveryProfitPercent: Double = 0.0,
    val profitToCollect: Double = 0.0,
    val subFeeOwed: Double,
    val adminDebtBalance: Double,
    val collectedCashBalance: Double,
    val dailyRate: Double,
    val carriedOverBalance: Double,
    val totalBalanceDue: Double,
    val accumulatedDeliveryFeesOwed: Double = 0.0,
    val accumulatedProfitToCollect: Double = 0.0,
    val accumulatedTotalBalanceDue: Double = 0.0,
    val nextBillingDate: LocalDate,
    val recentOrders: List<DriverOrderFinanceItem>
)

data class DriverOrderFinanceItem(
    val id: Long,
    val createdAt: Instant,
    val deliveredAt: Instant? = null,
    val totalAmount: Double,
    val deliveryFee: Double,
    val deliveryProfit: Double = 0.0,
    val status: OrderStatus,
    val isSettledDriver: Boolean,
    val orderType: String = "FOOD_DELIVERY",
    val deliveryZoneName: String? = null
)
