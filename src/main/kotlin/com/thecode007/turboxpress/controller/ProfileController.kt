package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.security.decorator.PermissionDecorator
import com.thecode007.turboxpress.service.ProfileService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

@RestController
@RequestMapping("/api/profile")
class ProfileController(
    private val profileService: ProfileService
) {
    private val logger = LoggerFactory.getLogger(ProfileController::class.java)

    @GetMapping("/status")
    fun getStatus(
        @AuthenticationPrincipal principal: PermissionDecorator
    ): ResponseEntity<String> {
        val userId = UUID.fromString(principal.getUserId())
        val role = principal.getRoleNames().firstOrNull() ?: "CUSTOMER"
        logger.info("Fetching status for user: $userId, role: $role")
        val status = profileService.getStatus(userId, role)
        return ResponseEntity.ok("\"$status\"")
    }

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadDocuments(
        @AuthenticationPrincipal principal: PermissionDecorator,
        @RequestParam("role") role: String,
        @RequestParam("firstName") firstName: String,
        @RequestParam("lastName") lastName: String,
        @RequestParam(value = "profilePic", required = false) profilePic: MultipartFile?,
        @RequestParam(value = "doc1", required = false) doc1: MultipartFile?,
        @RequestParam(value = "doc2", required = false) doc2: MultipartFile?,
        @RequestParam allParams: Map<String, String>
    ): ResponseEntity<BaseResponse<Nothing>> {
        val userId = UUID.fromString(principal.getUserId())
        logger.info("Upload request received for user: $userId, role: $role, name: $firstName $lastName")
        
        // Filter out known params to get "additionalData"
        val additionalData = allParams.filterKeys { 
            it !in setOf("role", "firstName", "lastName", "profilePic", "doc1", "doc2") 
        }

        return try {
            profileService.uploadDocuments(
                userId = userId,
                role = role,
                firstName = firstName,
                lastName = lastName,
                profilePic = profilePic,
                doc1 = doc1,
                doc2 = doc2,
                additionalData = additionalData
            )
            logger.info("Successfully processed upload for user: $userId")
            ResponseEntity.ok(BaseResponse.success("Documents uploaded successfully"))
        } catch (e: Exception) {
            logger.error("Upload failed for user: $userId. Error: ${e.message}", e)
            ResponseEntity.internalServerError().body(BaseResponse.error("Upload failed: ${e.message}"))
        }
    }

    @GetMapping("/driver/online-status")
    fun getOnlineStatus(
        @AuthenticationPrincipal principal: PermissionDecorator
    ): ResponseEntity<BaseResponse<String>> {
        val userId = UUID.fromString(principal.getUserId())
        return try {
            val status = profileService.getOnlineStatus(userId)
            ResponseEntity.ok(BaseResponse.success("Online status retrieved", status))
        } catch (e: Exception) {
            logger.error("Failed to get online status for user: $userId", e)
            ResponseEntity.internalServerError().body(BaseResponse.error("Failed to get status: ${e.message}"))
        }
    }

    @PatchMapping("/driver/online-status")
    fun updateOnlineStatus(
        @AuthenticationPrincipal principal: PermissionDecorator,
        @RequestParam status: String
    ): ResponseEntity<BaseResponse<Nothing>> {
        val userId = UUID.fromString(principal.getUserId())
        return try {
            profileService.updateOnlineStatus(userId, status)
            ResponseEntity.ok(BaseResponse.success("Online status updated to $status"))
        } catch (e: Exception) {
            logger.error("Failed to update online status for user: $userId", e)
            ResponseEntity.internalServerError().body(BaseResponse.error("Failed to update status: ${e.message}"))
        }
    }
}
