package com.thecode007.turboxpress.security

import com.thecode007.turboxpress.entity.User
import com.thecode007.turboxpress.security.decorator.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

object UserPrincipal {

    /**
     * Creates a principal carrying ALL of the user's roles and permissions.
     * Used for legacy admin-panel password login and internal lookups.
     */
    fun create(user: User): PermissionDecorator {
        var principal: PermissionDecorator = BaseUserPrincipal(user)

        user.roles.forEach { role ->
            principal = when (role.roleName) {
                "CUSTOMER" -> CustomerPermissionDecorator(principal)
                "COURIER"  -> CourierPermissionDecorator(principal)
                "MERCHANT" -> MerchantPermissionDecorator(principal)
                "ADMIN"    -> AdminPermissionDecorator(principal)
                else       -> principal
            }
        }

        return principal
    }

    /**
     * Creates a principal scoped to a SINGLE [targetRole].
     *
     * This is the heart of the Profile Partitioning Pattern. Even if the user
     * holds 3 profiles, the resulting principal only carries authorities for
     * the one role they are currently logged in as. This prevents cross-role
     * permission leakage at the Spring Security authority level.
     *
     * @param user       The core identity entity.
     * @param targetRole One of: CUSTOMER, COURIER, MERCHANT, ADMIN
     */
    fun createForRole(user: User, targetRole: String): PermissionDecorator {
        val base: PermissionDecorator = object : BaseUserPrincipal(user) {
            // Override authorities to expose ONLY the target role.
            override fun getAuthorities(): Collection<GrantedAuthority> =
                listOf(SimpleGrantedAuthority("ROLE_$targetRole"))

            override fun getRoleNames(): Set<String> = setOf(targetRole)
        }

        return when (targetRole) {
            "CUSTOMER" -> CustomerPermissionDecorator(base)
            "COURIER"  -> CourierPermissionDecorator(base)
            "MERCHANT" -> MerchantPermissionDecorator(base)
            "ADMIN"    -> AdminPermissionDecorator(base)
            else       -> base
        }
    }

    fun createWithContext(user: User, additionalContext: Map<String, Any>): PermissionDecorator {
        val principal = create(user)

        return object : PermissionDecorator by principal {
            override fun getContext(): Map<String, Any> {
                return principal.getContext() + additionalContext
            }
        }
    }
}
