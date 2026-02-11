package com.thecode007.turboxpress.security

import com.thecode007.turboxpress.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(phoneNumber: String): UserDetails {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { UsernameNotFoundException("User not found with phone number: $phoneNumber") }

        return UserPrincipal.create(user)
    }

    fun loadUserById(userId: String): UserDetails {
        val user = userRepository.findById(java.util.UUID.fromString(userId))
            .orElseThrow { UsernameNotFoundException("User not found with id: $userId") }

        return UserPrincipal.create(user)
    }
}
