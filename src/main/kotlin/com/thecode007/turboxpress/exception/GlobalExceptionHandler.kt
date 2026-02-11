package com.thecode007.turboxpress.exception

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.ErrorDetail
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(
        ex: InvalidCredentialsException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val response = BaseResponse.error<Nothing>(
            message = ex.message ?: "Invalid credentials",
            code = "UNAUTHORIZED"
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response)
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(
        ex: UserNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val response = BaseResponse.notFound<Nothing>(
            message = ex.message ?: "User not found"
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(UsernameNotFoundException::class)
    fun handleUsernameNotFound(
        ex: UsernameNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val response = BaseResponse.notFound<Nothing>(
            message = ex.message ?: "User not found"
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(
        ex: BadCredentialsException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val response = BaseResponse.error<Nothing>(
            message = "Invalid credentials",
            code = "UNAUTHORIZED"
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val response = BaseResponse.error<Nothing>(
            message = ex.message ?: "Access denied",
            code = "FORBIDDEN"
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val errorDetails = ex.bindingResult.allErrors.map { error ->
            val fieldName = (error as? FieldError)?.field
            val message = error.defaultMessage ?: "Invalid value"
            ErrorDetail(
                field = fieldName,
                message = message,
                code = "INVALID_FIELD"
            )
        }

        val response = BaseResponse.validationError<Nothing>(
            message = "Validation failed",
            errors = errorDetails
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val response = BaseResponse.error<Nothing>(
            message = ex.message ?: "An unexpected error occurred",
            code = "INTERNAL_SERVER_ERROR"
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}
