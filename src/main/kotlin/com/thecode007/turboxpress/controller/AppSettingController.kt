package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.AppSettingResponse
import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.UpdateAppSettingRequest
import com.thecode007.turboxpress.service.AppSettingService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/settings")
class AppSettingController(
    private val appSettingService: AppSettingService
) {

    @GetMapping
    fun getSettings(): BaseResponse<AppSettingResponse> {
        return BaseResponse.success("Settings retrieved successfully", appSettingService.getSettings())
    }

    @PutMapping
    fun updateSettings(@RequestBody request: UpdateAppSettingRequest): BaseResponse<AppSettingResponse> {
        return BaseResponse.success("Settings updated successfully", appSettingService.updateSettings(request))
    }
}
