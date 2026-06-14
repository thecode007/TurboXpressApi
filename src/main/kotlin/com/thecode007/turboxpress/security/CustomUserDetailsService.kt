package com.thecode007.turboxpress.security

import com.thecode007.turboxpress.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    /**
     * Standard load by phone number - returns a principal with ALL roles.
     * Used by legacy admin-panel login and Spring Security's auth mechanism.
     */
    override fun loadUserByUsername(phoneNumber: String): UserDetails {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { UsernameNotFoundException("User not found with phone number: $phoneNumber") }

        return UserPrincipal.create(user)
    }

    fun loadUserById(userId: String): UserDetails {
        val user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow { UsernameNotFoundException("User not found with id: $userId") }

        return UserPrincipal.create(user)
    }

    /**
     * Role-scoped load - returns a principal carrying ONLY [activeRole] authorities.
     *
     * Called by [JwtAuthenticationFilter] when an activeRole claim is present in the JWT.
     * Ensures the SecurityContext only exposes the role the user is currently operating under,
     * preventing cross-role permission leakage on every subsequent API request.
     *
     * @param phoneNumber The user's phone number (the JWT subject).
     * @param activeRole  The single role to scope to (CUSTOMER / COURIER / MERCHANT / ADMIN).
     */
    fun loadUserByUsernameAndRole(phoneNumber: String, activeRole: String): UserDetails {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { UsernameNotFoundException("User not found with phone number: $phoneNumber") }

        return UserPrincipal.createForRole(user, activeRole)
    }
}
