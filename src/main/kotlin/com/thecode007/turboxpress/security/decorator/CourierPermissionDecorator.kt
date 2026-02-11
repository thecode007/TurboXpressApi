package com.thecode007.turboxpress.security.decorator

import org.springframework.security.core.GrantedAuthority

class CourierPermissionDecorator(
    private val wrapped: PermissionDecorator
) : PermissionDecorator by wrapped {

    companion object {
        const val VEHICLE_MANAGEMENT = "VEHICLE_MANAGEMENT"
        const val LIVE_TRACKING = "LIVE_TRACKING"
        const val DELIVERY_MANAGEMENT = "DELIVERY_MANAGEMENT"
        const val ROUTE_OPTIMIZATION = "ROUTE_OPTIMIZATION"
    }

    override fun getPermissions(): Set<String> {
        return wrapped.getPermissions() + setOf(
            VEHICLE_MANAGEMENT,
            LIVE_TRACKING,
            DELIVERY_MANAGEMENT,
            ROUTE_OPTIMIZATION
        )
    }

    override fun getContext(): Map<String, Any> {
        val context = wrapped.getContext().toMutableMap()
        context["vehicleType"] = "MOTORCYCLE"
        context["deliveryZone"] = "ZONE_A"
        context["maxCapacity"] = 50
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
