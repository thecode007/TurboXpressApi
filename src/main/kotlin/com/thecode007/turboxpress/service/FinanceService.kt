package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.OrderFinanceItem
import com.thecode007.turboxpress.dto.RestaurantFinanceSummary
import com.thecode007.turboxpress.entity.OrderStatus
import com.thecode007.turboxpress.dto.DriverFinanceSummary
import com.thecode007.turboxpress.dto.DriverOrderFinanceItem
import com.thecode007.turboxpress.entity.BillingCycle
import com.thecode007.turboxpress.exception.ResourceNotFoundException
import com.thecode007.turboxpress.repository.DriverProfileRepository
import com.thecode007.turboxpress.repository.UserRepository
import com.thecode007.turboxpress.repository.OrderRepository
import com.thecode007.turboxpress.repository.RestaurantRepository
import com.thecode007.turboxpress.repository.AppSettingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Service
class FinanceService(
    private val orderRepository: OrderRepository,
    private val restaurantRepository: RestaurantRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val userRepository: UserRepository,
    private val appSettingRepository: AppSettingRepository
) {

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    fun getRestaurantSummary(restaurantId: Long): RestaurantFinanceSummary {
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { ResourceNotFoundException("Restaurant not found with id: $restaurantId") }

        val orders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
            .filter { it.status == OrderStatus.DELIVERED }

        val grossSales = orders
            .sumOf { it.totalAmount }

        val commissionsOwed = orders
            .filter { !it.isSettled }
            .sumOf { it.platformCommissionAmount }

        val now = LocalDate.now()
        val subFeeOwed = if (now.isAfter(restaurant.nextBillingDate) || now.isEqual(restaurant.nextBillingDate)) {
            restaurant.monthlySubFee
        } else {
            0.0
        }

        val totalBalanceDue = subFeeOwed + restaurant.balance.toDouble()
        
        val recentOrders = orders.take(20).map {
            OrderFinanceItem(
                id = it.id,
                createdAt = it.createdAt,
                totalAmount = it.totalAmount,
                platformCommissionAmount = it.platformCommissionAmount,
                status = it.status,
                isSettled = it.isSettled
            )
        }

        return RestaurantFinanceSummary(
            restaurantId = restaurant.id,
            restaurantName = restaurant.name,
            grossSales = grossSales,
            commissionsOwed = commissionsOwed,
            subFeeOwed = subFeeOwed,
            balance = restaurant.balance.toDouble(),
            carriedOverBalance = restaurant.carriedOverBalance,
            totalBalanceDue = totalBalanceDue,
            nextBillingDate = restaurant.nextBillingDate,
            recentOrders = recentOrders
        )
    }

    @Transactional
    fun settleRestaurant(restaurantId: Long, amount: Double? = null): RestaurantFinanceSummary {
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { ResourceNotFoundException("Restaurant not found with id: $restaurantId") }

        // Compute total owed before settling
        val summary = getRestaurantSummary(restaurantId)
        val totalDue = summary.totalBalanceDue
        
        if (amount != null && amount > totalDue) {
            throw IllegalArgumentException("Settlement amount ($amount) cannot exceed total due ($totalDue)")
        }
        
        val collectedAmount = amount ?: totalDue

        // Mark all unsettled delivered orders as settled
        val unsettledOrders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
            .filter { !it.isSettled && it.status == OrderStatus.DELIVERED }

        unsettledOrders.forEach { it.isSettled = true }
        orderRepository.saveAll(unsettledOrders)

        // Handle balance reset
        // balance is positive for debt. So debt = balance.
        // After collecting 'collectedAmount', the new balance should reflect the remaining debt.
        
        // totalDue = subFeeOwed + restaurant.balance
        // If we collect 'collectedAmount', the remaining debt is totalDue - collectedAmount.
        // New balance = remainingDebt
        val remainingDebt = totalDue - collectedAmount
        restaurant.balance = BigDecimal.valueOf(remainingDebt)
        restaurant.carriedOverBalance = 0.0 // Clearing legacy field as we now use 'balance'

        val now = LocalDate.now()
        if (now.isAfter(restaurant.nextBillingDate) || now.isEqual(restaurant.nextBillingDate)) {
            restaurant.nextBillingDate = restaurant.nextBillingDate.plusMonths(1)
        }
        restaurantRepository.save(restaurant)

        return getRestaurantSummary(restaurantId)
    }

    private data class PeriodWindow(
        val period: String,
        val selectedDate: String,
        val startDate: LocalDate?,
        val endDate: LocalDate?,
        val startInstant: Instant?,
        val endInstant: Instant?,
        val previousDate: String?,
        val nextDate: String?,
        val isBeforeCurrentPeriod: Boolean
    )

    private fun resolvePeriodWindow(period: String?, date: String?, month: String?): PeriodWindow {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val currentMonth = YearMonth.now(zoneId)

        val rawPeriod = period?.trim()?.uppercase()
        val effectiveDateInput = month?.trim()?.takeIf { it.isNotEmpty() }
            ?: date?.trim()?.takeIf { it.isNotEmpty() }

        val normalizedPeriod = when {
            rawPeriod == "ALL" -> "ALL"
            rawPeriod == "MONTHLY" || rawPeriod == "MONTH" || !month.isNullOrBlank() ||
                (effectiveDateInput != null && effectiveDateInput.length == 7) -> "MONTHLY"
            else -> "DAILY"
        }

        return when (normalizedPeriod) {
            "MONTHLY" -> {
                val targetMonth = if (effectiveDateInput.isNullOrBlank()) {
                    currentMonth
                } else {
                    runCatching { YearMonth.parse(effectiveDateInput) }
                        .recoverCatching { YearMonth.from(LocalDate.parse(effectiveDateInput)) }
                        .getOrElse {
                            throw IllegalArgumentException("Invalid month format: '$effectiveDateInput'. Expected YYYY-MM or YYYY-MM-DD")
                        }
                }
                val startDate = targetMonth.atDay(1)
                val endDate = targetMonth.atEndOfMonth()
                val startInstant = startDate.atStartOfDay(zoneId).toInstant()
                val endInstant = targetMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant()
                val isBeforeCurrent = targetMonth.isBefore(currentMonth)

                PeriodWindow(
                    period = "MONTHLY",
                    selectedDate = targetMonth.toString(),
                    startDate = startDate,
                    endDate = endDate,
                    startInstant = startInstant,
                    endInstant = endInstant,
                    previousDate = targetMonth.minusMonths(1).toString(),
                    nextDate = targetMonth.plusMonths(1).toString(),
                    isBeforeCurrentPeriod = isBeforeCurrent
                )
            }
            "DAILY" -> {
                val targetDate = if (effectiveDateInput.isNullOrBlank()) {
                    today
                } else {
                    runCatching { LocalDate.parse(effectiveDateInput) }
                        .getOrElse {
                            throw IllegalArgumentException("Invalid date format: '$effectiveDateInput'. Expected YYYY-MM-DD")
                        }
                }
                val startInstant = targetDate.atStartOfDay(zoneId).toInstant()
                val endInstant = targetDate.plusDays(1).atStartOfDay(zoneId).toInstant()
                val isBeforeCurrent = targetDate.isBefore(today)

                PeriodWindow(
                    period = "DAILY",
                    selectedDate = targetDate.toString(),
                    startDate = targetDate,
                    endDate = targetDate,
                    startInstant = startInstant,
                    endInstant = endInstant,
                    previousDate = targetDate.minusDays(1).toString(),
                    nextDate = targetDate.plusDays(1).toString(),
                    isBeforeCurrentPeriod = isBeforeCurrent
                )
            }
            else -> PeriodWindow(
                period = "ALL",
                selectedDate = "ALL",
                startDate = null,
                endDate = null,
                startInstant = null,
                endInstant = null,
                previousDate = null,
                nextDate = null,
                isBeforeCurrentPeriod = false
            )
        }
    }

    private fun roundTwoDecimals(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    fun getDriverSummary(
        phoneNumber: String,
        period: String? = null,
        date: String? = null,
        month: String? = null
    ): DriverFinanceSummary {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { ResourceNotFoundException("User not found with phone: $phoneNumber") }
        val driver = user.id?.let { driverProfileRepository.findById(it).orElse(null) }
            ?: throw ResourceNotFoundException("Driver not found with phone: $phoneNumber")

        val allDeliveredOrders = orderRepository.findByDriverUserPhoneNumberOrderByCreatedAtDesc(phoneNumber)
            .filter { it.status == OrderStatus.DELIVERED }
            .sortedByDescending { it.deliveredAt ?: it.createdAt }

        val setting = appSettingRepository.findById(1L).orElse(null)
        val profitPercent = setting?.deliveryProfitPercent ?: 0.0
        val profitRate = when {
            profitPercent > 1.0 -> profitPercent / 100.0
            profitPercent > 0.0 -> profitPercent
            else -> 0.0
        }

        // All-time accumulated unsettled totals
        val allUnsettledOrders = allDeliveredOrders.filter { !it.isSettledDriver }
        val accumulatedDeliveryFeesOwed = roundTwoDecimals(allUnsettledOrders.sumOf { it.deliveryFee })
        val accumulatedProfitToCollect = roundTwoDecimals(accumulatedDeliveryFeesOwed * profitRate)
        val accumulatedTotalBalanceDue = roundTwoDecimals(accumulatedProfitToCollect + driver.adminDebtBalance.toDouble())

        // Resolve requested period (DAILY, MONTHLY, or ALL)
        val window = resolvePeriodWindow(period, date, month)
        val ordersInPeriod = if (window.startInstant != null && window.endInstant != null) {
            allDeliveredOrders.filter { order ->
                val orderInstant = order.deliveredAt ?: order.createdAt
                !orderInstant.isBefore(window.startInstant) && orderInstant.isBefore(window.endInstant)
            }
        } else {
            allDeliveredOrders
        }

        val unsettledInPeriod = ordersInPeriod.filter { !it.isSettledDriver }

        val periodTotalDeliveryFees = roundTwoDecimals(ordersInPeriod.sumOf { it.deliveryFee })
        val periodTotalProfit = roundTwoDecimals(periodTotalDeliveryFees * profitRate)
        val periodDeliveryFeesOwed = roundTwoDecimals(unsettledInPeriod.sumOf { it.deliveryFee })
        val periodProfitToCollect = roundTwoDecimals(periodDeliveryFeesOwed * profitRate)
        val periodSettledProfit = roundTwoDecimals((periodTotalProfit - periodProfitToCollect).coerceAtLeast(0.0))
        val isPeriodSettled = unsettledInPeriod.isEmpty()

        val hasPrevious = window.period != "ALL"
        val hasNext = if (window.period != "ALL" && window.endInstant != null) {
            window.isBeforeCurrentPeriod || allDeliveredOrders.any { (it.deliveredAt ?: it.createdAt) >= window.endInstant }
        } else {
            false
        }

        val effectiveDeliveryFeesOwed = if (window.period == "ALL") accumulatedDeliveryFeesOwed else periodDeliveryFeesOwed
        val effectiveProfitToCollect = if (window.period == "ALL") accumulatedProfitToCollect else periodProfitToCollect
        val effectiveTotalBalanceDue = if (window.period == "ALL") accumulatedTotalBalanceDue else periodProfitToCollect

        val ordersToReturn = if (window.period == "ALL") ordersInPeriod.take(20) else ordersInPeriod
        val recentOrders = ordersToReturn.map {
            val itemProfit = roundTwoDecimals(it.deliveryFee * profitRate)
            DriverOrderFinanceItem(
                id = it.id,
                createdAt = it.createdAt,
                deliveredAt = it.deliveredAt,
                totalAmount = it.totalAmount,
                deliveryFee = it.deliveryFee,
                deliveryProfit = itemProfit,
                status = it.status,
                isSettledDriver = it.isSettledDriver,
                orderType = it.orderType.name,
                deliveryZoneName = it.customer.deliveryZone?.name
            )
        }

        return DriverFinanceSummary(
            phoneNumber = user.phoneNumber,
            fullName = driver.displayName ?: user.fullName,
            period = window.period,
            selectedDate = window.selectedDate,
            startDate = window.startDate,
            endDate = window.endDate,
            previousDate = window.previousDate,
            nextDate = if (hasNext) window.nextDate else null,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            periodOrderCount = ordersInPeriod.size,
            periodUnsettledOrderCount = unsettledInPeriod.size,
            periodTotalDeliveryFees = periodTotalDeliveryFees,
            periodTotalProfit = periodTotalProfit,
            periodDeliveryFeesOwed = periodDeliveryFeesOwed,
            periodProfitToCollect = periodProfitToCollect,
            periodSettledProfit = periodSettledProfit,
            isPeriodSettled = isPeriodSettled,
            deliveryFeesOwed = effectiveDeliveryFeesOwed,
            deliveryProfitPercent = profitPercent,
            profitToCollect = effectiveProfitToCollect,
            subFeeOwed = driver.adminDebtBalance.toDouble(), // Repurpose for UI compatibility
            adminDebtBalance = driver.adminDebtBalance.toDouble(),
            collectedCashBalance = driver.collectedCashBalance.toDouble(),
            dailyRate = driver.dailyRate.toDouble(),
            carriedOverBalance = driver.carriedOverBalance,
            totalBalanceDue = effectiveTotalBalanceDue,
            accumulatedDeliveryFeesOwed = accumulatedDeliveryFeesOwed,
            accumulatedProfitToCollect = accumulatedProfitToCollect,
            accumulatedTotalBalanceDue = accumulatedTotalBalanceDue,
            nextBillingDate = driver.nextBillingDate ?: LocalDate.now(),
            recentOrders = recentOrders
        )
    }

    @Transactional
    fun settleDriver(
        phoneNumber: String,
        amount: Double? = null,
        period: String? = null,
        date: String? = null,
        month: String? = null
    ): DriverFinanceSummary {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { ResourceNotFoundException("User not found with phone: $phoneNumber") }
        val driver = user.id?.let { driverProfileRepository.findById(it).orElse(null) }
            ?: throw ResourceNotFoundException("Driver not found with phone: $phoneNumber")

        val isPeriodScoped = (!period.isNullOrBlank() && period.trim().uppercase() != "ALL") ||
            !date.isNullOrBlank() || !month.isNullOrBlank()

        if (isPeriodScoped) {
            val summary = getDriverSummary(phoneNumber, period, date, month)
            val periodDue = summary.periodProfitToCollect
            val maxSettleable = roundTwoDecimals(periodDue + driver.adminDebtBalance.toDouble().coerceAtLeast(0.0))

            if (amount != null && amount > maxSettleable + 0.01) {
                throw IllegalArgumentException("Settlement amount ($amount) cannot exceed total due ($maxSettleable)")
            }

            val collectedAmount = amount ?: periodDue
            val window = resolvePeriodWindow(period, date, month)

            val unsettledOrdersInPeriod = orderRepository.findByDriverUserPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .filter { order ->
                    if (order.isSettledDriver || order.status != OrderStatus.DELIVERED) return@filter false
                    val orderInstant = order.deliveredAt ?: order.createdAt
                    window.startInstant != null && window.endInstant != null &&
                        !orderInstant.isBefore(window.startInstant) && orderInstant.isBefore(window.endInstant)
                }

            unsettledOrdersInPeriod.forEach { it.isSettledDriver = true }
            orderRepository.saveAll(unsettledOrdersInPeriod)

            val newDebt = roundTwoDecimals(driver.adminDebtBalance.toDouble() + (periodDue - collectedAmount))
            driver.adminDebtBalance = BigDecimal.valueOf(newDebt)
            driver.collectedCashBalance = BigDecimal.ZERO
            driver.carriedOverBalance = 0.0
        } else {
            // Settle entire accumulated balance
            val summary = getDriverSummary(phoneNumber, period = "ALL")
            val totalDue = summary.accumulatedTotalBalanceDue

            if (amount != null && amount > totalDue + 0.01) {
                throw IllegalArgumentException("Settlement amount ($amount) cannot exceed total due ($totalDue)")
            }

            val collectedAmount = amount ?: totalDue

            val unsettledOrders = orderRepository.findByDriverUserPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .filter { !it.isSettledDriver && it.status == OrderStatus.DELIVERED }

            unsettledOrders.forEach { it.isSettledDriver = true }
            orderRepository.saveAll(unsettledOrders)

            val remainingDebt = roundTwoDecimals(totalDue - collectedAmount)
            driver.adminDebtBalance = BigDecimal.valueOf(remainingDebt)
            driver.collectedCashBalance = BigDecimal.ZERO // Resetting cash as it was settled
            driver.carriedOverBalance = 0.0 // Clear legacy field
        }

        val now = LocalDate.now()
        val nextBillingDate = driver.nextBillingDate ?: now
        if (now.isAfter(nextBillingDate) || now.isEqual(nextBillingDate)) {
            driver.nextBillingDate = when (driver.billingCycle) {
                BillingCycle.DAILY -> nextBillingDate.plusDays(1)
                BillingCycle.WEEKLY -> nextBillingDate.plusWeeks(1)
                BillingCycle.MONTHLY -> nextBillingDate.plusMonths(1)
            }
        }
        driverProfileRepository.save(driver)

        return getDriverSummary(phoneNumber, period, date, month)
    }
}
