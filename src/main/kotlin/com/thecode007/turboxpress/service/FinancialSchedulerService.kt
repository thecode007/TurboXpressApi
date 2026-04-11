package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.repository.DeliveryGuyRepository
import com.thecode007.turboxpress.repository.RestaurantRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FinancialSchedulerService(
    private val restaurantRepository: RestaurantRepository,
    private val deliveryGuyRepository: DeliveryGuyRepository
) {
    private val logger = LoggerFactory.getLogger(FinancialSchedulerService::class.java)

    /**
     * Executes daily at midnight to settle restaurant subscriptions and driver salaries.
     * Cron: "0 0 0 * * ?" (Seconds Minutes Hours DayOfMonth Month DayOfWeek)
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    fun runDailySettlement() {
        logger.info("Starting daily financial settlement...")

        val driversUpdated = deliveryGuyRepository.accrueDailySalaries()
        logger.info("Accrued daily salaries for $driversUpdated active delivery guys.")

        logger.info("Daily financial settlement completed successfully.")
    }
}
