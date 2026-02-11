package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.ImpersonateResponse
import com.thecode007.turboxpress.dto.PageResponse
import com.thecode007.turboxpress.dto.UserResponse
import com.thecode007.turboxpress.entity.User
import com.thecode007.turboxpress.exception.UserNotFoundException
import com.thecode007.turboxpress.repository.UserRepository
import com.thecode007.turboxpress.security.JwtService
import com.thecode007.turboxpress.security.UserPrincipal
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
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

    fun getUserById(userId: UUID): User {
        return userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }
    }

    fun impersonateUser(targetUserId: UUID, adminId: String): ImpersonateResponse {
        val targetUser = getUserById(targetUserId)
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
            fullName = this.fullName,
            phoneNumber = this.phoneNumber,
            isActive = this.isActive,
            createdAt = this.createdAt!!,
            roles = this.roles.map { it.roleName }.toSet()
        )
    }
}
