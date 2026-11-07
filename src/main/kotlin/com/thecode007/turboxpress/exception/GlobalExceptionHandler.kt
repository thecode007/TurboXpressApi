package com.thecode007.turboxpress.exception

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.ErrorDetail
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(
        ex: ResourceNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val response = BaseResponse.notFound<Nothing>(
            message = ex.message ?: "Resource not found"
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException::class)
    fun handleNoResourceFound(
        ex: org.springframework.web.servlet.resource.NoResourceFoundException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val response = BaseResponse.notFound<Nothing>(
            message = "Endpoint not found: ${request.method} ${request.requestURI}"
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        log.warn("Illegal argument on [{} {}]: {}", request.method, request.requestURI, ex.message)
        val response = BaseResponse.error<Nothing>(
            message = ex.message ?: "Invalid request argument",
            code = "BAD_REQUEST"
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

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
            val rejectedValue = (error as? FieldError)?.rejectedValue
            val message = error.defaultMessage ?: "Invalid value"
            log.warn("Validation error on [{} {}] - field '{}': {} (rejected value: '{}')",
                request.method, request.requestURI, fieldName, message, rejectedValue)
            ErrorDetail(
                field = fieldName,
                message = message,
                code = "INVALID_FIELD"
            )
        }

        val joinedMessages = errorDetails.joinToString("; ") { "${it.field ?: "field"}: ${it.message}" }
        log.warn("Validation failed for [{} {}] with {} error(s): {}",
            request.method, request.requestURI, errorDetails.size, joinedMessages)

        val response = BaseResponse.validationError<Nothing>(
            message = "Validation failed: $joinedMessages",
            errors = errorDetails
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val rootCause = ex.mostSpecificCause
        val detailMsg = rootCause.message ?: ex.message ?: "Malformed JSON or unreadable request body"
        log.error("HTTP message unreadable on [{} {}]: {}", request.method, request.requestURI, detailMsg, ex)
        val response = BaseResponse.error<Nothing>(
            message = "Malformed request payload: $detailMsg",
            code = "BAD_REQUEST"
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        ex: org.springframework.dao.DataIntegrityViolationException,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        val rootCause = ex.mostSpecificCause
        val detailMsg = rootCause.message ?: ex.message ?: "Database constraint violation"
        log.error("Data integrity violation on [{} {}]: {}", request.method, request.requestURI, detailMsg, ex)
        val response = BaseResponse.error<Nothing>(
            message = "Database conflict: $detailMsg",
            code = "CONFLICT"
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<BaseResponse<Nothing>> {
        ex.printStackTrace() // Print the exact message and stacktrace to the console
        val stackTrace = ex.stackTraceToString()
        val response = BaseResponse.error<Nothing>(
            message = "Error: ${ex.message} \n\n StackTrace: $stackTrace",
            code = "INTERNAL_SERVER_ERROR"
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}
