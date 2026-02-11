package com.thecode007.turboxpress.security

import com.thecode007.turboxpress.entity.User
import com.thecode007.turboxpress.security.decorator.*

object UserPrincipal {
    fun create(user: User): PermissionDecorator {
        var principal: PermissionDecorator = BaseUserPrincipal(user)

        user.roles.forEach { role ->
            principal = when (role.roleName) {
                "CUSTOMER" -> CustomerPermissionDecorator(principal)
                "COURIER" -> CourierPermissionDecorator(principal)
                "MERCHANT" -> MerchantPermissionDecorator(principal)
                "ADMIN" -> AdminPermissionDecorator(principal)
                else -> principal
            }
        }

        return principal
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
