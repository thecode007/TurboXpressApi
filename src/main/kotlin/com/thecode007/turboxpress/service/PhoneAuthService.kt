package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.LoginResponse
import com.thecode007.turboxpress.dto.PhoneLoginRequest
import com.thecode007.turboxpress.dto.SetPasswordRequest
import com.thecode007.turboxpress.entity.*
import com.thecode007.turboxpress.repository.*
import com.thecode007.turboxpress.security.JwtService
import com.thecode007.turboxpress.security.UserPrincipal
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PhoneAuthService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val customerProfileRepository: CustomerProfileRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val ownerProfileRepository: OwnerProfileRepository,
    private val systemAdminProfileRepository: SystemAdminProfileRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder
) {
    private data class ProfileInfo(val id: UUID, val isComplete: Boolean, val status: VerificationStatus)

    @Transactional
    fun processPhoneLogin(request: PhoneLoginRequest): LoginResponse {
        val phoneNumber = request.phoneNumber
        val targetRole = request.targetRole.uppercase()

        validateTargetRole(targetRole)

        val user = findOrCreateUser(phoneNumber)
        ensureUserHasRole(user, targetRole)

        val profileInfo = findOrCreateProfile(user, targetRole)

        // Check password logic
        var requiresPasswordSetup = false
        var requiresPasswordEntry = false

        if (profileInfo.status == VerificationStatus.APPROVED) {
            val hasPassword = !user.passwordHash.isNullOrEmpty()
            if (!hasPassword) {
                requiresPasswordSetup = true
            } else {
                if (request.password.isNullOrEmpty()) {
                    requiresPasswordEntry = true
                } else if (!passwordEncoder.matches(request.password, user.passwordHash)) {
                    throw BadCredentialsException("Invalid password")
                }
            }
        }

        val principal = UserPrincipal.createForRole(user, targetRole)
        val token = jwtService.generateTokenForRole(principal, targetRole, profileInfo.id)

        return LoginResponse(
            token = token,
            userId = user.id.toString(),
            fullName = user.fullName,
            phoneNumber = user.phoneNumber,
            roles = principal.getRoleNames(),
            permissions = principal.getPermissions(),
            context = principal.getContext(),
            activeRole = targetRole,
            profileId = profileInfo.id.toString(),
            isProfileComplete = profileInfo.isComplete,
            verificationStatus = profileInfo.status.name,
            requiresPasswordSetup = requiresPasswordSetup,
            requiresPasswordEntry = requiresPasswordEntry
        )
    }

    @Transactional
    fun setPassword(request: SetPasswordRequest): LoginResponse {
        val phoneNumber = request.phoneNumber
        val targetRole = request.targetRole.uppercase()

        validateTargetRole(targetRole)

        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { IllegalArgumentException("User not found") }

        user.passwordHash = passwordEncoder.encode(request.password)
        userRepository.save(user)

        ensureUserHasRole(user, targetRole)
        val profileInfo = findOrCreateProfile(user, targetRole)

        val principal = UserPrincipal.createForRole(user, targetRole)
        val token = jwtService.generateTokenForRole(principal, targetRole, profileInfo.id)

        return LoginResponse(
            token = token,
            userId = user.id.toString(),
            fullName = user.fullName,
            phoneNumber = user.phoneNumber,
            roles = principal.getRoleNames(),
            permissions = principal.getPermissions(),
            context = principal.getContext(),
            activeRole = targetRole,
            profileId = profileInfo.id.toString(),
            isProfileComplete = profileInfo.isComplete,
            verificationStatus = profileInfo.status.name,
            requiresPasswordSetup = false,
            requiresPasswordEntry = false
        )
    }

    private fun validateTargetRole(role: String) {
        val allowed = setOf("CUSTOMER", "COURIER", "MERCHANT", "ADMIN")
        if (role !in allowed) {
            throw IllegalArgumentException(
                "Invalid targetRole '$role'. Must be one of: ${allowed.joinToString()}"
            )
        }
    }

    private fun findOrCreateUser(phoneNumber: String): User {
        val byPhone = userRepository.findByPhoneNumber(phoneNumber)
        if (byPhone.isPresent) {
            return byPhone.get()
        }

        val newUser = User(
            fullName = "User",
            username = phoneNumber,
            phoneNumber = phoneNumber,
            passwordHash = "", // Initial empty password
            isActive = true
        )
        return userRepository.save(newUser)
    }

    private fun ensureUserHasRole(user: User, roleName: String) {
        val alreadyHas = user.roles.any { it.roleName == roleName }
        if (!alreadyHas) {
            val role = roleRepository.findByRoleName(roleName)
                .orElseThrow { IllegalStateException("Role '$roleName' not found") }
            user.roles.add(role)
            userRepository.save(user)
        }
    }

    private fun findOrCreateProfile(user: User, targetRole: String): ProfileInfo {
        val userId = user.id!!
        return when (targetRole) {
            "CUSTOMER" -> {
                val profile = customerProfileRepository.findById(userId).orElseGet {
                    customerProfileRepository.save(
                        CustomerProfile(
                            userId = userId,
                            user = user,
                            displayName = user.fullName,
                            verificationStatus = VerificationStatus.APPROVED
                        )
                    )
                }
                ProfileInfo(userId, profile.isComplete(), profile.verificationStatus)
            }
            "COURIER" -> {
                val profile = driverProfileRepository.findById(userId).orElseGet {
                    driverProfileRepository.save(
                        DriverProfile(
                            userId = userId,
                            user = user,
                            displayName = user.fullName,
                            verificationStatus = VerificationStatus.PENDING
                        )
                    )
                }
                ProfileInfo(userId, profile.isComplete(), profile.verificationStatus)
            }
            "MERCHANT" -> {
                val profile = ownerProfileRepository.findById(userId).orElseGet {
                    ownerProfileRepository.save(
                        OwnerProfile(
                            userId = userId,
                            user = user,
                            verificationStatus = VerificationStatus.PENDING
                        )
                    )
                }
                ProfileInfo(userId, profile.isComplete(), profile.verificationStatus)
            }
            "ADMIN" -> {
                val profile = systemAdminProfileRepository.findById(userId).orElseGet {
                    systemAdminProfileRepository.save(
                        SystemAdminProfile(
                            userId = userId,
                            user = user,
                            verificationStatus = VerificationStatus.APPROVED
                        )
                    )
                }
                ProfileInfo(userId, profile.isComplete(), profile.verificationStatus)
            }
            else -> throw IllegalArgumentException("Unhandled role: $targetRole")
        }
    }
}
