package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.LoginRequest
import com.thecode007.turboxpress.dto.LoginResponse
import com.thecode007.turboxpress.exception.InvalidCredentialsException
import com.thecode007.turboxpress.repository.UserRepository
import com.thecode007.turboxpress.security.JwtService
import com.thecode007.turboxpress.security.UserPrincipal
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {

    fun login(request: LoginRequest): LoginResponse {
        val user = if (isPhoneNumber(request.identifier)) {
            userRepository.findByPhoneNumber(request.identifier)
        } else {
            userRepository.findByUsername(request.identifier)
        }.orElseThrow { InvalidCredentialsException("Invalid credentials") }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidCredentialsException("Invalid credentials")
        }

        if (!user.isActive) {
            throw InvalidCredentialsException("Account is inactive")
        }

        val userPrincipal = UserPrincipal.create(user) as PermissionDecorator
        val token = jwtService.generateToken(userPrincipal)

        return LoginResponse(
            token = token,
            userId = userPrincipal.getUserId(),
            fullName = userPrincipal.getFullName(),
            phoneNumber = userPrincipal.getPhoneNumber(),
            roles = userPrincipal.getRoleNames(),
            permissions = userPrincipal.getPermissions(),
            context = userPrincipal.getContext()
        )
    }

    private fun isPhoneNumber(identifier: String): Boolean {
        return identifier.startsWith("+") || identifier.matches(Regex("^[0-9]+$"))
    }
}
