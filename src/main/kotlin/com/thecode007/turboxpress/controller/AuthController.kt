package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.LoginRequest
import com.thecode007.turboxpress.dto.LoginResponse
import com.thecode007.turboxpress.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import org.springframework.security.core.annotation.AuthenticationPrincipal

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<BaseResponse<LoginResponse>> {
        val response = authService.login(request)
        return ResponseEntity.ok(BaseResponse.success("Login successful", response))
    }

    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal principal: PermissionDecorator?): ResponseEntity<BaseResponse<Nothing>> {
        if (principal != null) {
            authService.logout(principal)
        }
        return ResponseEntity.ok(BaseResponse.success("Logout successful", null))
    }
}
