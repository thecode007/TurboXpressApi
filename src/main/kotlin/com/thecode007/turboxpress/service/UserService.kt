package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.*
import com.thecode007.turboxpress.entity.User
import com.thecode007.turboxpress.exception.UserNotFoundException
import com.thecode007.turboxpress.repository.RoleRepository
import com.thecode007.turboxpress.repository.UserRepository
import com.thecode007.turboxpress.security.JwtService
import com.thecode007.turboxpress.security.UserPrincipal
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    @Value("\${jwt.impersonation.expiration}")
    private val impersonationExpiration: Long
) {

    fun getAllUsers(pageable: Pageable): PageResponse<UserResponse> {
        val usersPage = userRepository.findAll(pageable)
        val userResponses = usersPage.content.map { it.toUserResponse() }
        
        return PageResponse(
            content = userResponses,
            pageNumber = usersPage.number,
            pageSize = usersPage.size,
            totalElements = usersPage.totalElements,
            totalPages = usersPage.totalPages,
            isLast = usersPage.isLast
        )
    }

    fun getUserById(userId: UUID): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }
        return user.toUserResponse()
    }
    
    fun createUser(request: CreateUserRequest): UserResponse {
        // Get roles
        val roles = roleRepository.findAllById(request.roleIds).toMutableSet()
        
        // Create user
        val user = User(
            username = request.username,
            fullName = request.fullName,
            phoneNumber = request.phoneNumber,
            passwordHash = passwordEncoder.encode(request.password) ?: "",
            isActive = true,
            roles = roles
        )
        
        val savedUser = userRepository.save(user)
        return savedUser.toUserResponse()
    }
    
    fun updateUser(userId: UUID, request: UpdateUserRequest): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }
        
        // Get roles
        val roles = roleRepository.findAllById(request.roleIds).toMutableSet()
        
        // Update user
        user.fullName = request.fullName
        user.phoneNumber = request.phoneNumber
        user.isActive = request.isActive
        user.roles = roles
        
        val updatedUser = userRepository.save(user)
        return updatedUser.toUserResponse()
    }
    
    fun deleteUser(userId: UUID) {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException("User not found with id: $userId")
        }
        userRepository.deleteById(userId)
    }

    fun impersonateUser(targetUserId: UUID, adminId: String): ImpersonateResponse {
        val targetUser = userRepository.findById(targetUserId)
            .orElseThrow { UserNotFoundException("User not found with id: $targetUserId") }
        val userPrincipal = UserPrincipal.create(targetUser) as PermissionDecorator
        
        val token = jwtService.generateImpersonationToken(
            targetUserId = targetUserId.toString(),
            adminId = adminId,
            userDetails = userPrincipal
        )

        return ImpersonateResponse(
            token = token,
            adminId = adminId,
            targetUser = targetUser.toUserResponse(),
            expiresIn = impersonationExpiration
        )
    }

    private fun User.toUserResponse(): UserResponse {
        return UserResponse(
            id = this.id!!,
            username = this.username,
            fullName = this.fullName,
            phoneNumber = this.phoneNumber,
            isActive = this.isActive,
            createdAt = this.createdAt!!,
            roles = this.roles.map { it.roleName }.toSet()
        )
    }
}
