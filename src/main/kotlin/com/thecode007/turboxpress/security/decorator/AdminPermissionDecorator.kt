package com.thecode007.turboxpress.security.decorator

import org.springframework.security.core.GrantedAuthority

class AdminPermissionDecorator(
    private val wrapped: PermissionDecorator
) : PermissionDecorator by wrapped {

    companion object {
        const val USER_MANAGEMENT = "USER_MANAGEMENT"
        const val SYSTEM_CONFIGURATION = "SYSTEM_CONFIGURATION"
        const val IMPERSONATION = "IMPERSONATION"
        const val AUDIT_ACCESS = "AUDIT_ACCESS"
        const val FULL_ANALYTICS = "FULL_ANALYTICS"
    }

    override fun getPermissions(): Set<String> {
        return wrapped.getPermissions() + setOf(
            USER_MANAGEMENT,
            SYSTEM_CONFIGURATION,
            IMPERSONATION,
            AUDIT_ACCESS,
            FULL_ANALYTICS
        )
    }

    override fun getContext(): Map<String, Any> {
        val context = wrapped.getContext().toMutableMap()
        context["adminLevel"] = "SUPER_ADMIN"
        context["accessScope"] = "GLOBAL"
        return context
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        val baseAuthorities = wrapped.authorities.toMutableList()
        getPermissions().forEach { permission ->
            baseAuthorities.add(org.springframework.security.core.authority.SimpleGrantedAuthority("PERMISSION_$permission"))
        }
        return baseAuthorities
    }
}
