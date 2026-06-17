package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.OrderFinanceItem
import com.thecode007.turboxpress.dto.RestaurantFinanceSummary
import com.thecode007.turboxpress.entity.OrderStatus
import com.thecode007.turboxpress.dto.DriverFinanceSummary
import com.thecode007.turboxpress.dto.DriverOrderFinanceItem
import com.thecode007.turboxpress.entity.BillingCycle
import com.thecode007.turboxpress.exception.ResourceNotFoundException
import com.thecode007.turboxpress.repository.DeliveryGuyRepository
import com.thecode007.turboxpress.repository.OrderRepository
import com.thecode007.turboxpress.repository.RestaurantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class FinanceService(
    private val orderRepository: OrderRepository,
    private val restaurantRepository: RestaurantRepository,
    private val deliveryGuyRepository: DeliveryGuyRepository
) {

    fun getRestaurantSummary(restaurantId: Long): RestaurantFinanceSummary {
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { ResourceNotFoundException("Restaurant not found with id: $restaurantId") }

        val orders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)

        val grossSales = orders
            .filter { it.status != OrderStatus.CANCELLED && it.status != OrderStatus.REJECTED }
            .sumOf { it.totalAmount }

        val commissionsOwed = orders
            .filter { !it.isSettled && it.status != OrderStatus.CANCELLED && it.status != OrderStatus.REJECTED }
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

        // Mark all unsettled orders as settled
        val unsettledOrders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
            .filter { !it.isSettled }

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

    fun getDriverSummary(phoneNumber: String): DriverFinanceSummary {
        val driver = deliveryGuyRepository.findById(phoneNumber)
            .orElseThrow { ResourceNotFoundException("Driver not found with phone: $phoneNumber") }

        val orders = orderRepository.findByDriverPhoneNumberOrderByCreatedAtDesc(phoneNumber)

        val deliveryFeesOwed = orders
            .filter { !it.isSettledDriver && it.status != OrderStatus.CANCELLED && it.status != OrderStatus.REJECTED }
            .sumOf { it.deliveryFee }

        val totalBalanceDue = deliveryFeesOwed + driver.adminDebtBalance.toDouble()

        val recentOrders = orders.take(20).map {
            DriverOrderFinanceItem(
                id = it.id,
                createdAt = it.createdAt,
                totalAmount = it.totalAmount,
                deliveryFee = it.deliveryFee,
                status = it.status,
                isSettledDriver = it.isSettledDriver
            )
        }

        return DriverFinanceSummary(
            phoneNumber = driver.phoneNumber,
            fullName = driver.fullName,
            deliveryFeesOwed = deliveryFeesOwed,
            subFeeOwed = driver.adminDebtBalance.toDouble(), // Repurpose for UI compatibility
            adminDebtBalance = driver.adminDebtBalance.toDouble(),
            collectedCashBalance = driver.collectedCashBalance.toDouble(),
            dailyRate = driver.dailyRate.toDouble(),
            carriedOverBalance = driver.carriedOverBalance,
            totalBalanceDue = totalBalanceDue,
            nextBillingDate = driver.nextBillingDate,
            recentOrders = recentOrders
        )
    }

    @Transactional
    fun settleDriver(phoneNumber: String, amount: Double? = null): DriverFinanceSummary {
        val driver = deliveryGuyRepository.findById(phoneNumber)
            .orElseThrow { ResourceNotFoundException("Driver not found with phone: $phoneNumber") }

        // Compute total owed before settling
        val summary = getDriverSummary(phoneNumber)
        val totalDue = summary.totalBalanceDue

        if (amount != null && amount > totalDue) {
            throw IllegalArgumentException("Settlement amount ($amount) cannot exceed total due ($totalDue)")
        }

        val collectedAmount = amount ?: totalDue

        // Mark all unsettled orders as settled
        val unsettledOrders = orderRepository.findByDriverPhoneNumberOrderByCreatedAtDesc(phoneNumber)
            .filter { !it.isSettledDriver }

        unsettledOrders.forEach { it.isSettledDriver = true }
        orderRepository.saveAll(unsettledOrders)

        // Handle debt reset
        val remainingDebt = totalDue - collectedAmount
        driver.adminDebtBalance = BigDecimal.valueOf(remainingDebt)
        driver.collectedCashBalance = BigDecimal.ZERO // Resetting cash as it was settled
        driver.carriedOverBalance = 0.0 // Clear legacy field

        val now = LocalDate.now()
        if (now.isAfter(driver.nextBillingDate) || now.isEqual(driver.nextBillingDate)) {
            driver.nextBillingDate = when (driver.billingCycle) {
                BillingCycle.DAILY -> driver.nextBillingDate.plusDays(1)
                BillingCycle.WEEKLY -> driver.nextBillingDate.plusWeeks(1)
                BillingCycle.MONTHLY -> driver.nextBillingDate.plusMonths(1)
            }
        }
        deliveryGuyRepository.save(driver)

        return getDriverSummary(phoneNumber)
    }
}
