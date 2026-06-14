package com.thecode007.turboxpress.security

import com.thecode007.turboxpress.entity.User
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService {

    @Value("\${jwt.secret}")
    private lateinit var secret: String

    @Value("\${jwt.expiration}")
    private var expiration: Long = 86400000

    @Value("\${jwt.impersonation.expiration}")
    private var impersonationExpiration: Long = 3600000

    private fun getSigningKey(): SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    // --- Standard token (legacy + admin-panel login) ---------------------------

    fun generateToken(userDetails: UserDetails): String {
        val claims = mutableMapOf<String, Any>()

        if (userDetails is PermissionDecorator) {
            claims["roles"] = userDetails.getRoleNames()
            claims["permissions"] = userDetails.getPermissions()
            claims["userId"] = userDetails.getUserId()
            claims["fullName"] = userDetails.getFullName()
            claims["phoneNumber"] = userDetails.getPhoneNumber()
            claims["context"] = userDetails.getContext()
        }

        return createToken(claims, userDetails.username, expiration)
    }

    // --- Role-scoped token (Profile Partitioning Pattern) ---------------------

    /**
     * Generates a JWT scoped to a single active role.
     *
     * The [activeRole] claim is the "contextual gate": downstream filters verify
     * that the requested API path matches exactly this role, preventing cross-role
     * permission leakage even when the user holds multiple profiles.
     *
     * @param userDetails Principal built for the specific [activeRole] only.
     * @param activeRole  The role the user is currently operating under (CUSTOMER / DRIVER / MERCHANT / ADMIN).
     * @param profileId   UUID of the role-specific profile row for traceability.
     */
    fun generateTokenForRole(
        userDetails: UserDetails,
        activeRole: String,
        profileId: UUID
    ): String {
        val claims = mutableMapOf<String, Any>()
        claims["activeRole"] = activeRole
        claims["profileId"] = profileId.toString()

        if (userDetails is PermissionDecorator) {
            claims["roles"] = userDetails.getRoleNames()
            claims["permissions"] = userDetails.getPermissions()
            claims["userId"] = userDetails.getUserId()
            claims["fullName"] = userDetails.getFullName()
            claims["phoneNumber"] = userDetails.getPhoneNumber()
            claims["context"] = userDetails.getContext()
        }

        return createToken(claims, userDetails.username, expiration)
    }

    // --- Impersonation token (admin panel) ------------------------------------

    fun generateImpersonationToken(targetUserId: String, adminId: String, userDetails: UserDetails): String {
        val claims = mutableMapOf<String, Any>()
        claims["impersonation"] = true
        claims["adminId"] = adminId
        claims["targetUserId"] = targetUserId

        if (userDetails is PermissionDecorator) {
            claims["roles"] = userDetails.getRoleNames()
            claims["permissions"] = userDetails.getPermissions()
            claims["userId"] = userDetails.getUserId()
            claims["fullName"] = userDetails.getFullName()
            claims["phoneNumber"] = userDetails.getPhoneNumber()
            claims["context"] = userDetails.getContext()
        }

        return createToken(claims, userDetails.username, impersonationExpiration)
    }

    // --- Token creation --------------------------------------------------------

    private fun createToken(claims: Map<String, Any>, subject: String, expirationTime: Long): String {
        val now = Date()
        val expiryDate = Date(now.time + expirationTime)

        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey())
            .compact()
    }

    // --- Claims extraction -----------------------------------------------------

    fun extractUsername(token: String): String = extractClaim(token) { it.subject }
    fun extractUserId(token: String): String? = extractClaim(token) { it["userId"] as? String }
    fun extractProfileId(token: String): String? = extractClaim(token) { it["profileId"] as? String }

    /**
     * Extracts the activeRole claim - the single role this JWT session is scoped to.
     * Returns null for legacy tokens that pre-date the Profile Partitioning feature.
     */
    fun extractActiveRole(token: String): String? = extractClaim(token) { it["activeRole"] as? String }

    fun isImpersonationToken(token: String): Boolean = extractClaim(token) { it["impersonation"] as? Boolean } ?: false
    fun extractAdminId(token: String): String? = extractClaim(token) { it["adminId"] as? String }
    fun extractExpiration(token: String): Date = extractClaim(token) { it.expiration }

    private fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload
    }

    private fun isTokenExpired(token: String): Boolean = extractExpiration(token).before(Date())

    fun validateToken(token: String, userDetails: UserDetails): Boolean {
        val username = extractUsername(token)
        return username == userDetails.username && !isTokenExpired(token)
    }

    fun validateToken(token: String): Boolean {
        return try {
            !isTokenExpired(token)
        } catch (e: Exception) {
            false
        }
    }
}
