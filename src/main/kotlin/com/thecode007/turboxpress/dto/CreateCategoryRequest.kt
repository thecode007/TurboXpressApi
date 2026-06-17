package com.thecode007.turboxpress.dto

import jakarta.validation.constraints.NotBlank

data class CreateCategoryRequest(
    @field:NotBlank(message = "Category name is required")
    val name: String
)
