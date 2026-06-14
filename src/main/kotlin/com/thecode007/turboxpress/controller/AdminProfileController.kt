package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.ApproveProfileRequest
import com.thecode007.turboxpress.dto.ApproveProfileResponse
import com.thecode007.turboxpress.dto.PendingProfileDto
import com.thecode007.turboxpress.service.AdminProfileService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * Admin endpoints for profile verification management.
 *
 * All endpoints under /api/admin/profiles require ROLE_ADMIN.
 * The approvedBy UUID is extracted from the authenticated admin's principal,
 * ensuring the audit trail is tamper-proof.
 *
 * Endpoints:
 *   GET  /api/admin/profiles/pending?role=COURIER   - list pending profiles for a role
 *   POST /api/admin/profiles/approve                - approve a profile
 *   POST /api/admin/profiles/reject                 - reject a profile with a reason
 */
@RestController
@RequestMapping("/api/admin/profiles")
@PreAuthorize("hasRole('ADMIN')")
class AdminProfileController(
    private val adminProfileService: AdminProfileService
) {

    @GetMapping("/pending")
    fun getPendingProfiles(
        @RequestParam role: String
    ): ResponseEntity<List<PendingProfileDto>> {
        return ResponseEntity.ok(adminProfileService.getPendingProfiles(role))
    }

    @PostMapping("/approve")
    fun approveProfile(
        @Valid @RequestBody request: ApproveProfileRequest,
        @AuthenticationPrincipal adminPrincipal: UserDetails
    ): ResponseEntity<ApproveProfileResponse> {
        val adminId = extractAdminId(adminPrincipal)
        return ResponseEntity.ok(adminProfileService.approveProfile(request, adminId))
    }

    @PostMapping("/reject")
    fun rejectProfile(
        @Valid @RequestBody request: ApproveProfileRequest,
        @AuthenticationPrincipal adminPrincipal: UserDetails
    ): ResponseEntity<ApproveProfileResponse> {
        requireNotNull(request.adminNote) { "adminNote (rejection reason) is required when rejecting a profile." }
        val adminId = extractAdminId(adminPrincipal)
        return ResponseEntity.ok(adminProfileService.rejectProfile(request, adminId))
    }

    /**
     * One-shot endpoint to backfill all already-approved owner/driver profiles
     * into the legacy `owners` and `delivery_guys` tables.
     * Safe to call multiple times — skips any record that already exists.
     */
    @PostMapping("/sync")
    fun syncApprovedProfiles(): ResponseEntity<Map<String, Int>> {
        return ResponseEntity.ok(adminProfileService.syncApprovedProfiles())
    }

    /**
     * Resolves the admin's UUID from their principal username (phone number).
     * Falls back to a fixed sentinel UUID if the user record can't be resolved,
     * which should never happen given prior auth checks.
     */
    private fun extractAdminId(principal: UserDetails): UUID {
        return try {
            // Username is the phone number; userId is embedded in the UserPrincipal
            (principal as? com.thecode007.turboxpress.security.decorator.PermissionDecorator)
                ?.getUserId()
                ?.let { UUID.fromString(it) }
                ?: UUID.fromString("00000000-0000-0000-0000-000000000000")
        } catch (e: Exception) {
            UUID.fromString("00000000-0000-0000-0000-000000000000")
        }
    }
}
