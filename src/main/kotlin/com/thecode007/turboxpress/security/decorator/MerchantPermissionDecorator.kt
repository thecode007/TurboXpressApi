package com.thecode007.turboxpress.security.decorator

import org.springframework.security.core.GrantedAuthority
import java.util.*

class MerchantPermissionDecorator(
    private val wrapped: PermissionDecorator,
    private val storeId: String? = null
) : PermissionDecorator by wrapped {

    companion object {
        const val STORE_MANAGEMENT = "STORE_MANAGEMENT"
        const val INVENTORY_MANAGEMENT = "INVENTORY_MANAGEMENT"
        const val ORDER_MANAGEMENT = "ORDER_MANAGEMENT"
        const val ANALYTICS_ACCESS = "ANALYTICS_ACCESS"
    }

    override fun getPermissions(): Set<String> {
        return wrapped.getPermissions() + setOf(
            STORE_MANAGEMENT,
            INVENTORY_MANAGEMENT,
            ORDER_MANAGEMENT,
            ANALYTICS_ACCESS
        )
    }

    override fun getContext(): Map<String, Any> {
        val context = wrapped.getContext().toMutableMap()
        context["storeId"] = storeId ?: UUID.randomUUID().toString()
        context["storeName"] = "Sample Store"
        context["businessType"] = "RESTAURANT"
        context["operatingHours"] = mapOf("open" to "09:00", "close" to "22:00")
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
