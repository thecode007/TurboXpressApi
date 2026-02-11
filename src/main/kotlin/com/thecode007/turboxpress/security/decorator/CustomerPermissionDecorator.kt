package com.thecode007.turboxpress.security.decorator

import org.springframework.security.core.GrantedAuthority

class CustomerPermissionDecorator(
    private val wrapped: PermissionDecorator
) : PermissionDecorator by wrapped {

    companion object {
        const val ORDER_PLACEMENT = "ORDER_PLACEMENT"
        const val ORDER_TRACKING = "ORDER_TRACKING"
        const val PAYMENT_MANAGEMENT = "PAYMENT_MANAGEMENT"
        const val ADDRESS_MANAGEMENT = "ADDRESS_MANAGEMENT"
    }

    override fun getPermissions(): Set<String> {
        return wrapped.getPermissions() + setOf(
            ORDER_PLACEMENT,
            ORDER_TRACKING,
            PAYMENT_MANAGEMENT,
            ADDRESS_MANAGEMENT
        )
    }

    override fun getContext(): Map<String, Any> {
        val context = wrapped.getContext().toMutableMap()
        context["loyaltyPoints"] = 0
        context["preferredPaymentMethod"] = "CARD"
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
