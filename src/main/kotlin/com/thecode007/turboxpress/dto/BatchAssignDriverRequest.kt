package com.thecode007.turboxpress.dto

data class BatchAssignDriverRequest(
    val orderIds: List<Long>,
    val driverPhoneNumber: String
)

