package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.ImpersonateResponse
import com.thecode007.turboxpress.dto.PageResponse
import com.thecode007.turboxpress.dto.UserResponse
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import com.thecode007.turboxpress.service.UserService
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val userService: UserService
) {

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<BaseResponse<PageResponse<UserResponse>>> {
        val pageable = PageRequest.of(page, size)
        val users = userService.getAllUsers(pageable)
        return ResponseEntity.ok(BaseResponse.success("Users retrieved successfully", users))
    }

    @PostMapping("/impersonate/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun impersonateUser(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: PermissionDecorator
    ): ResponseEntity<BaseResponse<ImpersonateResponse>> {
        val response = userService.impersonateUser(id, principal.getUserId())
        return ResponseEntity.ok(BaseResponse.success("Impersonation successful", response))
    }
}
