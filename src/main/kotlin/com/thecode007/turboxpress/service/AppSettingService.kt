package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.AppSettingResponse
import com.thecode007.turboxpress.dto.UpdateAppSettingRequest
import com.thecode007.turboxpress.entity.AppSetting
import com.thecode007.turboxpress.repository.AppSettingRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppSettingService(
    private val appSettingRepository: AppSettingRepository,
    @org.springframework.context.annotation.Lazy private val orderService: OrderService
) {

    @Transactional
    @Cacheable(value = ["settings"])
    fun getSettings(): AppSettingResponse {
        val setting = appSettingRepository.findById(1L).orElseGet {
            appSettingRepository.save(AppSetting(id = 1L))
        }
        return AppSettingResponse(
            deliveryProfitPercent = setting.deliveryProfitPercent,
            restaurantSubscriptionFee = setting.restaurantSubscriptionFee,
            driverSubscriptionFee = setting.driverSubscriptionFee,
            pricePerKm = setting.pricePerKm,
            baseFare = setting.baseFare,
            isAutoAssignEnabled = setting.isAutoAssignEnabled
        )
    }

    @Transactional
    @CacheEvict(value = ["settings"], allEntries = true)
    fun updateSettings(request: UpdateAppSettingRequest): AppSettingResponse {
        val setting = appSettingRepository.findById(1L).orElseGet {
            AppSetting(id = 1L)
        }
        setting.deliveryProfitPercent = request.deliveryProfitPercent
        setting.restaurantSubscriptionFee = request.restaurantSubscriptionFee
        setting.driverSubscriptionFee = request.driverSubscriptionFee
        setting.pricePerKm = request.pricePerKm
        setting.baseFare = request.baseFare
        val wasAutoAssignEnabled = setting.isAutoAssignEnabled
        setting.isAutoAssignEnabled = request.isAutoAssignEnabled
        
        val saved = appSettingRepository.save(setting)

        // Trigger manual broadcast queue if switched to manual (auto-assign disabled)
        if (wasAutoAssignEnabled && !saved.isAutoAssignEnabled) {
            orderService.broadcastNextPendingOrder()
        }
        return AppSettingResponse(
            deliveryProfitPercent = saved.deliveryProfitPercent,
            restaurantSubscriptionFee = saved.restaurantSubscriptionFee,
            driverSubscriptionFee = saved.driverSubscriptionFee,
            pricePerKm = saved.pricePerKm,
            baseFare = saved.baseFare,
            isAutoAssignEnabled = saved.isAutoAssignEnabled
        )
    }
}
