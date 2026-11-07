package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.*
import com.thecode007.turboxpress.entity.OrderStatus
import com.thecode007.turboxpress.entity.VerificationStatus
import com.thecode007.turboxpress.repository.DriverProfileRepository
import com.thecode007.turboxpress.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class AnalyticsService(
    private val orderRepository: OrderRepository,
    private val driverProfileRepository: DriverProfileRepository
) {

    fun getAveragePrepareTime(restaurantId: Long, days: Int = 7): AveragePrepareTimeResponse {
        val effectiveDays = days.coerceAtLeast(1)
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val startDate = today.minusDays((effectiveDays - 1).toLong())
        val afterInstant = startDate.atStartOfDay(zoneId).toInstant()

        val orders = orderRepository.findByRestaurantIdAndCreatedAtAfterOrderByCreatedAtAsc(
            restaurantId,
            afterInstant
        )

        // Calculate all durations and overall average
        var totalMinutes = 0.0
        var validOrderCount = 0
        val dailySums = mutableMapOf<String, Double>()
        val dailyCounts = mutableMapOf<String, Int>()

        for (order in orders) {
            if (order.status == OrderStatus.CANCELLED || order.status == OrderStatus.REJECTED) continue

            val startInstant = order.acceptedAt ?: order.createdAt
            val endInstant = order.readyAt ?: order.pickedUpAt ?: order.deliveredAt ?: continue

            val minutes = Duration.between(startInstant, endInstant).toSeconds() / 60.0
            // Ignore negative durations due to strange data
            if (minutes < 0) continue

            totalMinutes += minutes
            validOrderCount++

            val dateStr = order.createdAt.atZone(zoneId).toLocalDate().toString()
            dailySums[dateStr] = (dailySums[dateStr] ?: 0.0) + minutes
            dailyCounts[dateStr] = (dailyCounts[dateStr] ?: 0) + 1
        }

        val overallAverage = if (validOrderCount == 0) 0.0 else roundTwoDecimals(totalMinutes / validOrderCount)

        // Build the last `effectiveDays` daily averages list
        val dailyAverages = mutableListOf<DailyAveragePrepareTime>()
        for (i in (effectiveDays - 1) downTo 0) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.toString()
            val dayName = date.dayOfWeek.name.take(1)

            val sum = dailySums[dateStr] ?: 0.0
            val count = dailyCounts[dateStr] ?: 0
            val avg = if (count > 0) roundTwoDecimals(sum / count) else 0.0

            dailyAverages.add(DailyAveragePrepareTime(dayName, avg))
        }

        return AveragePrepareTimeResponse(
            targetMinutes = 15,
            overallAverageMinutes = overallAverage,
            dailyAverages = dailyAverages
        )
    }

    fun getOrdersPerDay(restaurantId: Long, days: Int = 30): OrdersPerDayResponse {
        val effectiveDays = days.coerceAtLeast(1)
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val startDate = today.minusDays((effectiveDays - 1).toLong())
        val afterInstant = startDate.atStartOfDay(zoneId).toInstant()

        val orders = orderRepository.findByRestaurantIdAndCreatedAtAfterOrderByCreatedAtAsc(
            restaurantId,
            afterInstant
        )

        val dailyCountsMap = mutableMapOf<String, Int>()

        for (order in orders) {
            if (order.status == OrderStatus.CANCELLED || order.status == OrderStatus.REJECTED) continue
            val dateStr = order.createdAt.atZone(zoneId).toLocalDate().toString()
            dailyCountsMap[dateStr] = (dailyCountsMap[dateStr] ?: 0) + 1
        }

        val dailyCounts = mutableListOf<DailyOrderCount>()
        for (i in (effectiveDays - 1) downTo 0) {
            val dateStr = today.minusDays(i.toLong()).toString()
            val count = dailyCountsMap[dateStr] ?: 0
            dailyCounts.add(DailyOrderCount(dateStr, count))
        }

        return OrdersPerDayResponse(
            totalOrders = dailyCounts.sumOf { it.count },
            durationDays = effectiveDays,
            dailyCounts = dailyCounts
        )
    }

    fun getDriverDeliveryStats(period: String = "DAILY"): DriverDeliveriesResponse {
        val now = Instant.now()
        val normalizedPeriod = period.uppercase()
        val afterInstant = when (normalizedPeriod) {
            "MONTHLY" -> now.minus(30, ChronoUnit.DAYS)
            "WEEKLY" -> now.minus(7, ChronoUnit.DAYS)
            else -> now.minus(24, ChronoUnit.HOURS)
        }
        val deliveredOrders = orderRepository.findDeliveredOrdersWithDriverAfter(afterInstant)
        val ordersByDriverId = deliveredOrders
            .filter { it.driver != null }
            .groupBy { it.driver!!.userId }

        val approvedDrivers = driverProfileRepository.findAllByVerificationStatusWithUser(VerificationStatus.APPROVED)
        val allDriverProfiles = LinkedHashMap<java.util.UUID, com.thecode007.turboxpress.entity.DriverProfile>()
        for (driver in approvedDrivers) {
            allDriverProfiles[driver.userId] = driver
        }
        for (order in deliveredOrders) {
            val driver = order.driver ?: continue
            allDriverProfiles.putIfAbsent(driver.userId, driver)
        }

        val stats = allDriverProfiles.map { (driverId, driver) ->
            val driverOrders = ordersByDriverId[driverId].orEmpty()
            val revenue = driverOrders.sumOf { order ->
                if (order.totalAmount > 0.0) order.totalAmount else order.deliveryFee
            }
            DriverDeliveryStatDto(
                driverId = driverId.toString(),
                driverFullName = driver.displayName ?: driver.user.fullName,
                driverPhoneNumber = driver.user.phoneNumber,
                deliveryCount = driverOrders.size.toLong(),
                totalRevenue = roundTwoDecimals(revenue)
            )
        }.sortedWith(compareByDescending<DriverDeliveryStatDto> { it.deliveryCount }.thenByDescending { it.totalRevenue })

        return DriverDeliveriesResponse(
            period = normalizedPeriod,
            totalDeliveries = deliveredOrders.size.toLong(),
            driverStats = stats
        )
    }

    private fun roundTwoDecimals(value: Double): Double {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}
