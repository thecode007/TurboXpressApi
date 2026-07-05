package com.thecode007.turboxpress.dto

interface ProfileResponseDto

data class DriverProfileResponseDto(
    val firstName: String,
    val lastName: String,
    val nationality: String,
    val profilePicUrl: String?,
    val document1Url: String?,
    val document2Url: String?
) : ProfileResponseDto

data class CustomerProfileResponseDto(
    val firstName: String,
    val lastName: String,
    val profilePicUrl: String?
) : ProfileResponseDto

data class OwnerProfileResponseDto(
    val businessName: String,
    val profilePicUrl: String?
) : ProfileResponseDto
