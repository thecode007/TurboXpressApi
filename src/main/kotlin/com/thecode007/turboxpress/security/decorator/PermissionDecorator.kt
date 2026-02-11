package com.thecode007.turboxpress.security.decorator

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Permission Decorator interface for dynamically adding capabilities to users based on their roles.
 * This follows the Decorator Pattern to extend user permissions beyond simple role strings.
 */
interface PermissionDecorator : UserDetails {
    fun getPermissions(): Set<String>
    fun getContext(): Map<String, Any>
    fun hasPermission(permission: String): Boolean = getPermissions().contains(permission)
    fun getUserId(): String
    fun getFullName(): String
    fun getPhoneNumber(): String
    fun getRoleNames(): Set<String>
}
