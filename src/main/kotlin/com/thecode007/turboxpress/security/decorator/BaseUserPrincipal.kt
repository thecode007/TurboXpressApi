package com.thecode007.turboxpress.security.decorator

import com.thecode007.turboxpress.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Base principal wrapping a [User] entity.
 * passwordHash is nullable; Firebase users have no local password.
 * getPassword() returns empty string for Firebase users so Spring Security
 * doesn't reject the principal - actual auth is done via the JWT filter.
 */
open class BaseUserPrincipal(
    private val user: User
) : PermissionDecorator {

    override fun getUserId(): String = user.id.toString()
    override fun getFullName(): String = user.fullName
    override fun getPhoneNumber(): String = user.phoneNumber
    override fun getRoleNames(): Set<String> = user.roles.map { it.roleName }.toSet()
    override fun getPermissions(): Set<String> = emptySet()
    override fun getContext(): Map<String, Any> = emptyMap()

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return user.roles.map { SimpleGrantedAuthority("ROLE_${it.roleName}") }
    }

    /** Returns the password hash, or empty string for Firebase-only users. */
    override fun getPassword(): String = user.passwordHash ?: ""
    override fun getUsername(): String = user.phoneNumber
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = user.isActive
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = user.isActive
}
