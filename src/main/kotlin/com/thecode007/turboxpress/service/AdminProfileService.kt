package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.ApproveProfileRequest
import com.thecode007.turboxpress.dto.ApproveProfileResponse
import com.thecode007.turboxpress.dto.PendingProfileDto
import com.thecode007.turboxpress.entity.*
import com.thecode007.turboxpress.repository.*
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Admin service for managing driver and owner profile approvals.
 *
 * Design principles:
 *  - **Idempotent**: calling approveProfile() on an already-approved profile is a no-op.
 *  - **Auditable**: every status change records approvedBy (admin's userId) and an optional adminNote.
 *  - **Clean separation**: no dependency on legacy tables - fresh start.
 */
@Service

class AdminProfileService(
    private val userRepository: UserRepository,
    private val customerProfileRepository: CustomerProfileRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val ownerProfileRepository: OwnerProfileRepository,
    private val systemAdminProfileRepository: SystemAdminProfileRepository,
    private val ownerRepository: OwnerRepository,
    private val restaurantRepository: RestaurantRepository
) {
    private val geometryFactory = GeometryFactory()

    /**
     * Sets a profile's [VerificationStatus] to APPROVED.
     * Idempotent - re-approving an already-approved profile simply updates the note and returns.
     *
     * @param userId  UUID of the user whose profile is being approved.
     * @param role    Which profile to approve: CUSTOMER / COURIER / MERCHANT / ADMIN
     * @param adminId UUID of the admin performing the action (for audit trail).
     * @param note    Optional free-text admin note attached to the approval.
     */
    @Transactional
    fun approveProfile(request: ApproveProfileRequest, adminId: UUID): ApproveProfileResponse {
        val userId = UUID.fromString(request.userId)
        val role = request.role.uppercase()

        val message = when (role) {
            "CUSTOMER" -> approveCustomerProfile(userId, adminId, request.adminNote)
            "COURIER"  -> approveDriverProfile(userId, adminId, request.adminNote)
            "MERCHANT" -> approveOwnerProfile(userId, adminId, request.adminNote)
            "ADMIN"    -> approveAdminProfile(userId, adminId, request.adminNote)
            else -> throw IllegalArgumentException("Unknown role '$role'. Must be CUSTOMER, COURIER, MERCHANT, or ADMIN.")
        }

        return ApproveProfileResponse(
            userId = request.userId,
            role = role,
            status = VerificationStatus.APPROVED.name,
            approvedBy = adminId.toString(),
            message = message
        )
    }

    /**
     * Sets a profile's [VerificationStatus] to REJECTED.
     * Idempotent - rejecting an already-rejected profile just updates the note.
     *
     * @param userId  UUID of the user whose profile is being rejected.
     * @param role    Which profile to reject.
     * @param adminId UUID of the admin performing the action.
     * @param reason  Mandatory rejection reason - will be shown to the user.
     */
    @Transactional
    fun rejectProfile(request: ApproveProfileRequest, adminId: UUID): ApproveProfileResponse {
        val userId = UUID.fromString(request.userId)
        val role = request.role.uppercase()
        val reason = request.adminNote ?: "No reason provided"

        val message = setProfileStatus(userId, role, VerificationStatus.REJECTED, adminId, reason)

        return ApproveProfileResponse(
            userId = request.userId,
            role = role,
            status = VerificationStatus.REJECTED.name,
            approvedBy = adminId.toString(),
            message = message
        )
    }

    /**
     * Returns all profiles for a given role that are currently PENDING review.
     */
    fun getPendingProfiles(role: String): List<PendingProfileDto> {
        return when (role.uppercase()) {
            "COURIER" -> driverProfileRepository
                .findAllByVerificationStatus(VerificationStatus.PENDING)
                .map { p ->
                    PendingProfileDto(
                        userId = p.userId.toString(),
                        role = "COURIER",
                        displayName = p.displayName,
                        hasIdDocument = p.idDocumentUrl != null,
                        hasCriminalRecord = p.criminalRecordUrl != null,
                        submittedAt = p.createdAt?.toString(),
                        profilePictureUrl = p.profilePictureUrl,
                        idDocumentUrl = p.idDocumentUrl,
                        criminalRecordUrl = p.criminalRecordUrl
                    )
                }
            "MERCHANT" -> ownerProfileRepository
                .findAllByVerificationStatus(VerificationStatus.PENDING)
                .map { p ->
                    PendingProfileDto(
                        userId = p.userId.toString(),
                        role = "MERCHANT",
                        displayName = p.businessName,
                        hasIdDocument = p.idDocumentUrl != null,
                        hasCriminalRecord = p.criminalRecordUrl != null,
                        submittedAt = p.createdAt?.toString(),
                        profilePictureUrl = p.profilePictureUrl,
                        idDocumentUrl = p.idDocumentUrl,
                        criminalRecordUrl = p.criminalRecordUrl,
                        locationDescription = p.locationDescription,
                        restaurantLocation = p.restaurantLocation
                    )
                }
            else -> throw IllegalArgumentException("Only COURIER and MERCHANT profiles require manual approval.")
        }
    }

    // -----------------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------------

    private fun approveCustomerProfile(userId: UUID, adminId: UUID, note: String?): String {
        val profile = customerProfileRepository.findByUserId(userId)
            .orElseThrow { NoSuchElementException("CustomerProfile not found for userId=$userId") }

        if (profile.verificationStatus == VerificationStatus.APPROVED) {
            return "CustomerProfile for $userId is already APPROVED - no changes made."
        }

        profile.verificationStatus = VerificationStatus.APPROVED
        profile.approvedBy = adminId
        profile.adminNote = note
        customerProfileRepository.save(profile)
        return "CustomerProfile for $userId has been APPROVED."
    }

    private fun approveDriverProfile(userId: UUID, adminId: UUID, note: String?): String {
        val profile = driverProfileRepository.findByUserIdWithUser(userId)
            .orElseThrow { NoSuchElementException("DriverProfile not found for userId=$userId") }

        val alreadyApproved = profile.verificationStatus == VerificationStatus.APPROVED
        if (!alreadyApproved) {
            profile.verificationStatus = VerificationStatus.APPROVED
            profile.approvedBy = adminId
            profile.adminNote = note
            driverProfileRepository.save(profile)
        }

        return if (alreadyApproved) "DriverProfile for $userId was already APPROVED - no changes made."
               else "DriverProfile for $userId has been APPROVED."
    }

    private fun approveOwnerProfile(userId: UUID, adminId: UUID, note: String?): String {
        val profile = ownerProfileRepository.findByUserIdWithUser(userId)
            .orElseThrow { NoSuchElementException("OwnerProfile not found for userId=$userId") }

        val alreadyApproved = profile.verificationStatus == VerificationStatus.APPROVED
        if (!alreadyApproved) {
            profile.verificationStatus = VerificationStatus.APPROVED
            profile.approvedBy = adminId
            profile.adminNote = note
            ownerProfileRepository.save(profile)
        }

        // Sync to legacy owners table (idempotent)
        val user = profile.user
        val owner = if (!ownerRepository.existsById(user.phoneNumber)) {
            ownerRepository.save(
                Owner(
                    phoneNumber = user.phoneNumber,
                    fullName = profile.businessName ?: user.fullName,
                    passwordHash = user.passwordHash ?: "",
                    profilePictureUrl = profile.profilePictureUrl,
                    isActive = user.isActive
                )
            )
        } else {
            ownerRepository.findById(user.phoneNumber).get()
        }

        // Auto-create restaurant from wizard data if not already exists
        // Parse the "lat, lng" string submitted by the owner wizard into a proper geometry Point
        val restaurantName = profile.businessName ?: user.fullName
        if (!restaurantRepository.existsByName(restaurantName)) {
            restaurantRepository.save(
                Restaurant(
                    name = restaurantName,
                    logoUrl = profile.profilePictureUrl,
                    location = parseLocation(profile.restaurantLocation),
                    locationDescription = profile.locationDescription,
                    owner = owner,
                    monthlySubFee = 0.0,
                    commissionRate = 0.0
                )
            )
        }

        return if (alreadyApproved) "OwnerProfile for $userId was already APPROVED - synced to operational table."
               else "OwnerProfile for $userId has been APPROVED."
    }

    private fun approveAdminProfile(userId: UUID, adminId: UUID, note: String?): String {
        val profile = systemAdminProfileRepository.findByUserId(userId)
            .orElseThrow { NoSuchElementException("SystemAdminProfile not found for userId=$userId") }

        if (profile.verificationStatus == VerificationStatus.APPROVED) {
            return "SystemAdminProfile for $userId is already APPROVED - no changes made."
        }

        profile.verificationStatus = VerificationStatus.APPROVED
        systemAdminProfileRepository.save(profile)
        return "SystemAdminProfile for $userId has been APPROVED."
    }

    /**
     * Backfills the legacy `owners` and `delivery_guys` tables from already-approved profiles.
     * Safe to call multiple times — skips any entry that already exists.
     * Call this once via POST /api/admin/profiles/sync after deploying the fix.
     */
    @Transactional
    fun syncApprovedProfiles(): Map<String, Int> {
        var ownersSynced = 0
        var driversSynced = 0

        ownerProfileRepository.findAllByVerificationStatusWithUser(VerificationStatus.APPROVED).forEach { profile ->
            val user = profile.user
            val owner = if (!ownerRepository.existsById(user.phoneNumber)) {
                ownerRepository.save(
                    Owner(
                        phoneNumber = user.phoneNumber,
                        fullName = profile.businessName ?: user.fullName,
                        passwordHash = user.passwordHash ?: "",
                        profilePictureUrl = profile.profilePictureUrl,
                        isActive = user.isActive
                    )
                )
                ownersSynced++
                ownerRepository.findById(user.phoneNumber).get()
            } else {
                ownerRepository.findById(user.phoneNumber).get()
            }

            // Auto-create restaurant if not exists
            val restaurantName = profile.businessName ?: user.fullName
            if (!restaurantRepository.existsByName(restaurantName)) {
                restaurantRepository.save(
                    Restaurant(
                        name = restaurantName,
                        logoUrl = profile.profilePictureUrl,
                        location = parseLocation(profile.restaurantLocation),
                        locationDescription = profile.locationDescription,
                        owner = owner,
                        monthlySubFee = 0.0,
                        commissionRate = 0.0
                    )
                )
            }
        }



        return mapOf("ownersSynced" to ownersSynced, "driversSynced" to driversSynced)
    }

    /**
     * Parses a "lat, lng" coordinate string (as produced by the mobile map picker)
     * into a PostGIS-compatible JTS Point with SRID 4326.
     *
     * Note: JTS Coordinate(x, y) = Coordinate(longitude, latitude).
     * The mobile sends "latitude, longitude" so we swap the values.
     * Falls back to (0, 0) if the string is missing or malformed.
     */
    private fun parseLocation(locationString: String?): org.locationtech.jts.geom.Point {
        if (!locationString.isNullOrBlank()) {
            val parts = locationString.split(",")
            if (parts.size == 2) {
                val lat = parts[0].trim().toDoubleOrNull()
                val lng = parts[1].trim().toDoubleOrNull()
                if (lat != null && lng != null) {
                    val point = geometryFactory.createPoint(Coordinate(lng, lat)) // JTS: x=lng, y=lat
                    point.srid = 4326
                    return point
                }
            }
        }
        // Fallback to 0,0 if location wasn't provided
        val fallback = geometryFactory.createPoint(Coordinate(0.0, 0.0))
        fallback.srid = 4326
        return fallback
    }

    /** Generic status setter used by rejectProfile(). */
    private fun setProfileStatus(
        userId: UUID,
        role: String,
        status: VerificationStatus,
        adminId: UUID,
        note: String
    ): String {
        when (role) {
            "CUSTOMER" -> {
                val p = customerProfileRepository.findByUserId(userId)
                    .orElseThrow { NoSuchElementException("CustomerProfile not found for userId=$userId") }
                p.verificationStatus = status; p.approvedBy = adminId; p.adminNote = note
                customerProfileRepository.save(p)
            }
            "COURIER" -> {
                val p = driverProfileRepository.findByUserId(userId)
                    .orElseThrow { NoSuchElementException("DriverProfile not found for userId=$userId") }
                p.verificationStatus = status; p.approvedBy = adminId; p.adminNote = note
                driverProfileRepository.save(p)
            }
            "MERCHANT" -> {
                val p = ownerProfileRepository.findByUserId(userId)
                    .orElseThrow { NoSuchElementException("OwnerProfile not found for userId=$userId") }
                p.verificationStatus = status; p.approvedBy = adminId; p.adminNote = note
                ownerProfileRepository.save(p)
            }
            else -> throw IllegalArgumentException("Unknown role: $role")
        }
        return "$role profile for $userId has been set to ${status.name}."
    }
}
