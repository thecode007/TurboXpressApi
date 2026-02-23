package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.*
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import com.thecode007.turboxpress.service.UserService
import jakarta.validation.Valid
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
    
    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getUserById(@PathVariable id: UUID): ResponseEntity<BaseResponse<UserResponse>> {
        val user = userService.getUserById(id)
        return ResponseEntity.ok(BaseResponse.success("User retrieved successfully", user))
    }
    
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    fun createUser(
        @Valid @RequestBody request: CreateUserRequest
    ): ResponseEntity<BaseResponse<UserResponse>> {
        val user = userService.createUser(request)
        return ResponseEntity.ok(BaseResponse.success("User created successfully", user))
    }
    
    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateUser(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateUserRequest
    ): ResponseEntity<BaseResponse<UserResponse>> {
        val user = userService.updateUser(id, request)
        return ResponseEntity.ok(BaseResponse.success("User updated successfully", user))
    }
    
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteUser(@PathVariable id: UUID): ResponseEntity<BaseResponse<Nothing>> {
        userService.deleteUser(id)
        return ResponseEntity.ok(BaseResponse.success("User deleted successfully", null))
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
