package com.thecode007.turboxpress.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT Authentication Filter - runs once per request and populates the SecurityContext.
 *
 * Role-scoping behaviour:
 * - If the JWT contains an [activeRole] claim (Profile Partitioning flow), the filter
 *   calls [CustomUserDetailsService.loadUserByUsernameAndRole] to build a principal
 *   carrying ONLY that role's authorities. This is the first gate of scoping.
 * - If no [activeRole] is present (legacy admin-panel token), the filter falls back
 *   to [CustomUserDetailsService.loadUserByUsername] which loads all roles (unchanged behavior).
 *
 * The second gate - validating that the requested URL path matches the activeRole -
 * is enforced by [RoleScopeFilter] which runs after this filter.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: CustomUserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val jwt = extractJwtFromRequest(request)

            if (jwt != null && jwtService.validateToken(jwt)) {
                val phoneNumber = jwtService.extractUsername(jwt)
                val activeRole = jwtService.extractActiveRole(jwt)

                // Load a principal scoped to only the declared role (or all roles for legacy tokens)
                val userDetails = if (activeRole != null) {
                    userDetailsService.loadUserByUsernameAndRole(phoneNumber, activeRole)
                } else {
                    userDetailsService.loadUserByUsername(phoneNumber)
                }

                if (jwtService.validateToken(jwt, userDetails)) {
                    val authentication = UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.authorities
                    )
                    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                    if (jwtService.isImpersonationToken(jwt)) {
                        val adminId = jwtService.extractAdminId(jwt)
                        logger.info("Impersonation token: Admin=$adminId, Target=${jwtService.extractUserId(jwt)}")
                    }

                    SecurityContextHolder.getContext().authentication = authentication
                }
            }
        } catch (ex: Exception) {
            logger.error("Could not set user authentication in security context", ex)
        }

        filterChain.doFilter(request, response)
    }

    private fun extractJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }
}
