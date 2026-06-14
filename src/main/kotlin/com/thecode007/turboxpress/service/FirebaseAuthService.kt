package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.FirebaseLoginRequest
import com.thecode007.turboxpress.dto.LoginResponse
import com.thecode007.turboxpress.entity.*
import com.thecode007.turboxpress.repository.*
import com.thecode007.turboxpress.security.FirebaseTokenVerifier
import com.thecode007.turboxpress.security.JwtService
import com.thecode007.turboxpress.security.UserPrincipal
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Core Firebase authentication service implementing the Profile Partitioning Pattern.
 *
 * processUserLogin() is the single entry point for all 3 mobile apps:
 *   - Customer App  - targetRole = "CUSTOMER"
 *   - Driver App    - targetRole = "COURIER"
 *   - Owner App     - targetRole = "MERCHANT"
 *
 * It verifies the Firebase token, upserts the canonical User, lazily creates the
 * role-specific profile on first login, and returns a JWT scoped to that one role.
 */
@Service
class FirebaseAuthService(
    private val firebaseTokenVerifier: FirebaseTokenVerifier,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val customerProfileRepository: CustomerProfileRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val ownerProfileRepository: OwnerProfileRepository,
    private val systemAdminProfileRepository: SystemAdminProfileRepository,
    private val jwtService: JwtService
) {
    private data class ProfileInfo(val id: UUID, val isComplete: Boolean, val status: VerificationStatus)

    /**
     * Processes a login from any of the 3 mobile apps.
     *
     * Steps:
     *  1. Verify the Firebase ID token.
     *  2. Extract uid + phoneNumber from the verified token.
     *  3. Find or create the canonical [User] row.
     *  4. Ensure the user has the [targetRole] in the user_roles junction table.
     *  5. Find or create the profile for [targetRole].
     *  6. Build a role-scoped [UserPrincipal] (single role only).
     *  7. Generate a JWT with activeRole + profileId claims.
     *  8. Return [LoginResponse].
     *
     * @param request Contains the raw Firebase ID token and the targetRole.
     * @return [LoginResponse] with a scoped JWT.
     * @throws BadCredentialsException if the Firebase token is invalid.
     * @throws IllegalArgumentException if targetRole is unrecognised.
     */
    @Transactional
    fun processUserLogin(request: FirebaseLoginRequest): LoginResponse {
        // -- Step 1 & 2: Verify Firebase token ----------------------------------
        val firebaseToken = firebaseTokenVerifier.verify(request.firebaseToken!!)
        val firebaseUid = firebaseToken.uid
        val phoneNumber = firebaseTokenVerifier.extractPhoneNumber(firebaseToken)
        val displayName = firebaseToken.name ?: "User"
        val targetRole = request.targetRole!!.uppercase()

        validateTargetRole(targetRole)

        // -- Step 3: Find or create canonical User -------------------------------
        val user = findOrCreateUser(firebaseUid, phoneNumber, displayName)

        // -- Step 4: Ensure user has the target role -----------------------------
        ensureUserHasRole(user, targetRole)

        // -- Step 5: Find or create the role-specific profile -------------------
        val profileInfo = findOrCreateProfile(user, targetRole, displayName)

        // -- Step 6 & 7: Build scoped principal and generate JWT -----------------
        val principal = UserPrincipal.createForRole(user, targetRole)
        val token = jwtService.generateTokenForRole(principal, targetRole, profileInfo.id)

        return LoginResponse(
            token = token,
            userId = user.id.toString(),
            fullName = user.fullName,
            phoneNumber = user.phoneNumber,
            roles = principal.getRoleNames(),
            permissions = principal.getPermissions(),
            context = principal.getContext(),
            activeRole = targetRole,
            profileId = profileInfo.id.toString(),
            isProfileComplete = profileInfo.isComplete,
            verificationStatus = profileInfo.status.name
        )
    }

    // -----------------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------------

    private fun validateTargetRole(role: String) {
        val allowed = setOf("CUSTOMER", "COURIER", "MERCHANT", "ADMIN")
        if (role !in allowed) {
            throw IllegalArgumentException(
                "Invalid targetRole '$role'. Must be one of: ${allowed.joinToString()}"
            )
        }
    }

    /**
     * Finds a [User] by firebaseUid first, then by phoneNumber (for users who may have
     * registered via the legacy system). Creates a new User if none found.
     * Updates the firebaseUid if the user was found by phoneNumber but has no uid yet.
     */
    private fun findOrCreateUser(firebaseUid: String, phoneNumber: String, displayName: String): User {
        // Primary lookup: by Firebase UID
        val byUid = userRepository.findByFirebaseUid(firebaseUid)
        if (byUid.isPresent) return byUid.get()

        // Fallback: by phone number (handles legacy users)
        val byPhone = userRepository.findByPhoneNumber(phoneNumber)
        if (byPhone.isPresent) {
            val existing = byPhone.get()
            if (existing.firebaseUid == null) {
                existing.firebaseUid = firebaseUid
                return userRepository.save(existing)
            }
            return existing
        }

        // New user
        val newUser = User(
            fullName = displayName,
            username = phoneNumber,
            phoneNumber = phoneNumber,
            firebaseUid = firebaseUid,
            passwordHash = "", // Bypasses the legacy NOT NULL constraint in the database
            isActive = true
        )
        return userRepository.save(newUser)
    }

    /**
     * Ensures the [user] has [roleName] in their user_roles set.
     * Idempotent: does nothing if the role is already assigned.
     */
    private fun ensureUserHasRole(user: User, roleName: String) {
        val alreadyHas = user.roles.any { it.roleName == roleName }
        if (!alreadyHas) {
            val role = roleRepository.findByRoleName(roleName)
                .orElseThrow { IllegalStateException("Role '$roleName' not found in roles table. Ensure schema.sql has been applied.") }
            user.roles.add(role)
            userRepository.save(user)
        }
    }

    /**
     * Lazily creates the role-specific profile if it doesn't exist yet.
     * Returns the profile's info (ID, completion status, verification status).
     */
    private fun findOrCreateProfile(user: User, targetRole: String, displayName: String): ProfileInfo {
        val userId = user.id!!
        return when (targetRole) {
            "CUSTOMER" -> {
                val profile = customerProfileRepository.findById(userId).orElseGet {
                    customerProfileRepository.save(
                        CustomerProfile(
                            userId = userId,
                            user = user,
                            displayName = displayName,
                            verificationStatus = VerificationStatus.APPROVED
                        )
                    )
                }
                ProfileInfo(userId, profile.isComplete(), profile.verificationStatus)
            }
            "COURIER" -> {
                val profile = driverProfileRepository.findById(userId).orElseGet {
                    driverProfileRepository.save(
                        DriverProfile(
                            userId = userId,
                            user = user,
                            displayName = displayName,
                            verificationStatus = VerificationStatus.PENDING
                        )
                    )
                }
                ProfileInfo(userId, profile.isComplete(), profile.verificationStatus)
            }
            "MERCHANT" -> {
                val profile = ownerProfileRepository.findById(userId).orElseGet {
                    ownerProfileRepository.save(
                        OwnerProfile(
                            userId = userId,
                            user = user,
                            verificationStatus = VerificationStatus.PENDING
                        )
                    )
                }
                ProfileInfo(userId, profile.isComplete(), profile.verificationStatus)
            }
            "ADMIN" -> {
                val profile = systemAdminProfileRepository.findById(userId).orElseGet {
                    systemAdminProfileRepository.save(
                        SystemAdminProfile(
                            userId = userId,
                            user = user,
                            verificationStatus = VerificationStatus.APPROVED
                        )
                    )
                }
                ProfileInfo(userId, profile.isComplete(), profile.verificationStatus)
            }
            else -> throw IllegalArgumentException("Unhandled role: $targetRole")
        }
    }
}
