package com.thecode007.turboxpress.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Role Scope Filter - the second gate in the Profile Partitioning security model.
 *
 * While [JwtAuthenticationFilter] builds a principal with only one role's authorities,
 * this filter independently validates that the requested URL path is consistent with
 * the activeRole claim in the JWT. This prevents cross-role leakage where a user with
 * a CUSTOMER-scoped JWT tries to access /api/driver/... endpoints.
 *
 * Mapping (role to path prefix):
 *   CUSTOMER -> /api/customer/
 *   COURIER  -> /api/driver/
 *   MERCHANT -> /api/merchant/
 *   ADMIN    -> /api/admin/
 *
 * Paths not matching any scoped prefix pass through to Spring Security's URL matchers.
 * Tokens without an activeRole claim (legacy admin-panel tokens) bypass this check.
 */
@Component
class RoleScopeFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    companion object {
        private val EXEMPT_PREFIXES = listOf(
            "/api/auth",
            "/api/media",
            "/api/profile",
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator"
        )

        private val ROLE_PATH_MAP = mapOf(
            "CUSTOMER" to "/api/customer/",
            "COURIER"  to "/api/driver/",
            "MERCHANT" to "/api/merchant/",
            "ADMIN"    to "/api/admin/"
        )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI

        // Always pass through exempt paths
        if (EXEMPT_PREFIXES.any { path.startsWith(it) }) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = extractJwtFromRequest(request)

        // No JWT - let Spring Security handle the 401
        if (jwt == null || !jwtService.validateToken(jwt)) {
            filterChain.doFilter(request, response)
            return
        }

        val activeRole = jwtService.extractActiveRole(jwt)

        // Legacy token (no activeRole) - bypass for backward compatibility
        if (activeRole == null) {
            filterChain.doFilter(request, response)
            return
        }

        // Is this path in a role-scoped zone?
        val requestedScopedPrefix = ROLE_PATH_MAP.values.firstOrNull { path.startsWith(it) }

        // Not in a scoped zone - allow through (Spring Security URL matchers handle it)
        if (requestedScopedPrefix == null) {
            filterChain.doFilter(request, response)
            return
        }

        // Path is in a scoped zone - validate it matches the activeRole
        val allowedPrefix = ROLE_PATH_MAP[activeRole]
        if (allowedPrefix == null || !path.startsWith(allowedPrefix)) {
            sendForbiddenResponse(
                response,
                "Access denied: session role '$activeRole' cannot access '$path'. Log in with the correct role."
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun extractJwtFromRequest(request: HttpServletRequest): String? {
        val bearer = request.getHeader("Authorization")
        return if (bearer != null && bearer.startsWith("Bearer ")) bearer.substring(7) else null
    }

    private fun sendForbiddenResponse(response: HttpServletResponse, message: String) {
        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val safeMessage = message.replace("\"", "'")
        val body = "{\"error\":\"ROLE_SCOPE_VIOLATION\",\"message\":\"$safeMessage\"}"
        response.writer.write(body)
    }
}
