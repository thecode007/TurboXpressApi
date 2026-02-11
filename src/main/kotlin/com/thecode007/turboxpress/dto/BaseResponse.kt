package com.thecode007.turboxpress.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BaseResponse<T>(
    val success: Boolean,
    val message: String,
    val code: String,
    val data: T? = null,
    val errors: List<ErrorDetail>? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun <T> success(message: String = "Success", data: T? = null): BaseResponse<T> {
            return BaseResponse(
                success = true,
                message = message,
                code = "SUCCESS",
                data = data
            )
        }

        fun <T> error(
            message: String,
            code: String = "ERROR",
            data: T? = null,
            errors: List<ErrorDetail>? = null
        ): BaseResponse<T> {
            return BaseResponse(
                success = false,
                message = message,
                code = code,
                data = data,
                errors = errors
            )
        }

        fun <T> created(message: String = "Resource created successfully", data: T? = null): BaseResponse<T> {
            return BaseResponse(
                success = true,
                message = message,
                code = "CREATED",
                data = data
            )
        }

        fun <T> notFound(message: String = "Resource not found", data: T? = null): BaseResponse<T> {
            return BaseResponse(
                success = false,
                message = message,
                code = "NOT_FOUND",
                data = data
            )
        }

        fun <T> validationError(
            message: String = "Validation failed",
            errors: List<ErrorDetail>,
            data: T? = null
        ): BaseResponse<T> {
            return BaseResponse(
                success = false,
                message = message,
                code = "VALIDATION_ERROR",
                data = data,
                errors = errors
            )
        }
    }
}

data class ErrorDetail(
    val field: String? = null,
    val message: String,
    val code: String? = null
)
