package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.*
import com.thecode007.turboxpress.repository.OrderRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
class AnalyticsService(
    private val orderRepository: OrderRepository
) {

    fun getAveragePrepareTime(restaurantId: Long, days: Int = 7): AveragePrepareTimeResponse {
        val afterInstant = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val orders = orderRepository.findByRestaurantIdAndAcceptedAtIsNotNullAndReadyAtIsNotNullAndCreatedAtAfterOrderByCreatedAtAsc(
            restaurantId, 
            afterInstant
        )

        val zoneId = ZoneId.systemDefault()
        
        // Calculate all durations and overall average
        var totalMinutes = 0.0
        val dailySums = mutableMapOf<String, Double>()
        val dailyCounts = mutableMapOf<String, Int>()

        for (order in orders) {
            val minutes = Duration.between(order.acceptedAt, order.readyAt).toMinutes().toDouble()
            // Ignore negative durations due to strange data
            if (minutes < 0) continue

            totalMinutes += minutes
            
            val zdt = order.createdAt.atZone(zoneId)
            // e.g., "M", "T", "W"
            val dayName = zdt.dayOfWeek.name.take(1)
            // But we actually want to group by exact day to get the average per day
            val dateStr = zdt.toLocalDate().toString()
            
            dailySums[dateStr] = (dailySums[dateStr] ?: 0.0) + minutes
            dailyCounts[dateStr] = (dailyCounts[dateStr] ?: 0) + 1
        }

        val overallAverage = if (orders.isEmpty()) 0.0 else totalMinutes / orders.size

        // Build the last `days` daily averages list
        val dailyAverages = mutableListOf<DailyAveragePrepareTime>()
        for (i in (days - 1) downTo 0) {
            val date = Instant.now().minus(i.toLong(), ChronoUnit.DAYS).atZone(zoneId).toLocalDate()
            val dateStr = date.toString()
            val dayName = date.dayOfWeek.name.take(1)
            
            val sum = dailySums[dateStr] ?: 0.0
            val count = dailyCounts[dateStr] ?: 0
            val avg = if (count > 0) sum / count else 0.0
            
            dailyAverages.add(DailyAveragePrepareTime(dayName, avg))
        }

        return AveragePrepareTimeResponse(
            targetMinutes = 15,
            overallAverageMinutes = overallAverage,
            dailyAverages = dailyAverages
        )
    }

    fun getOrdersPerDay(restaurantId: Long, days: Int = 30): OrdersPerDayResponse {
        val afterInstant = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val orders = orderRepository.findByRestaurantIdAndCreatedAtAfterOrderByCreatedAtAsc(
            restaurantId, 
            afterInstant
        )

        val zoneId = ZoneId.systemDefault()
        val dailyCountsMap = mutableMapOf<String, Int>()

        for (order in orders) {
            val dateStr = order.createdAt.atZone(zoneId).toLocalDate().toString()
            dailyCountsMap[dateStr] = (dailyCountsMap[dateStr] ?: 0) + 1
        }

        val dailyCounts = mutableListOf<DailyOrderCount>()
        for (i in (days - 1) downTo 0) {
            val dateStr = Instant.now().minus(i.toLong(), ChronoUnit.DAYS).atZone(zoneId).toLocalDate().toString()
            val count = dailyCountsMap[dateStr] ?: 0
            dailyCounts.add(DailyOrderCount(dateStr, count))
        }

        return OrdersPerDayResponse(
            totalOrders = orders.size,
            durationDays = days,
            dailyCounts = dailyCounts
        )
    }

    fun getDriverDeliveryStats(period: String = "DAILY"): DriverDeliveriesResponse {
        val now = Instant.now()
        val afterInstant = when (period.uppercase()) {
            "MONTHLY" -> now.minus(30, ChronoUnit.DAYS)
            else -> now.minus(24, ChronoUnit.HOURS)
        }
        val deliveredOrders = orderRepository.findByStatusAndDeliveredAtAfterOrderByDeliveredAtDesc(
            com.thecode007.turboxpress.entity.OrderStatus.DELIVERED,
            afterInstant
        )

        val stats = deliveredOrders
            .filter { it.driver != null }
            .groupBy { it.driver }
            .map { (driver, orders) ->
                DriverDeliveryStatDto(
                    driverId = driver?.userId?.toString(),
                    driverFullName = driver?.displayName ?: driver?.user?.fullName ?: "Unknown",
                    driverPhoneNumber = driver?.user?.phoneNumber,
                    deliveryCount = orders.size.toLong(),
                    totalRevenue = orders.sumOf { it.totalAmount }
                )
            }
            .sortedByDescending { it.deliveryCount }

        return DriverDeliveriesResponse(
            period = period.uppercase(),
            totalDeliveries = deliveredOrders.size.toLong(),
            driverStats = stats
        )
    }
}
