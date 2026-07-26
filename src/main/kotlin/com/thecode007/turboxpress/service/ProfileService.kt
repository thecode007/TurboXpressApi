package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.entity.VerificationStatus
import com.thecode007.turboxpress.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.*

@Service
class ProfileService(
    private val customerProfileRepository: CustomerProfileRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val ownerProfileRepository: OwnerProfileRepository,
    private val mediaService: MediaService,
    @org.springframework.context.annotation.Lazy private val orderService: OrderService
) {
    private val logger = LoggerFactory.getLogger(ProfileService::class.java)

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    fun getStatus(userId: UUID, role: String): String {
        return when (role.uppercase()) {
            "CUSTOMER" -> customerProfileRepository.findById(userId).map { it.verificationStatus.name }.orElse("PENDING")
            "COURIER" -> driverProfileRepository.findById(userId).map { it.verificationStatus.name }.orElse("PENDING")
            "MERCHANT" -> ownerProfileRepository.findById(userId).map { it.verificationStatus.name }.orElse("PENDING")
            else -> "PENDING"
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    fun getMyProfile(userId: UUID, role: String): com.thecode007.turboxpress.dto.ProfileResponseDto {
        return when (role.uppercase()) {
            "CUSTOMER" -> {
                val profile = customerProfileRepository.findById(userId).orElseThrow { Exception("Customer profile not found") }
                val parts = profile.displayName?.split(" ", limit = 2) ?: listOf("", "")
                val firstName = parts.getOrNull(0) ?: ""
                val lastName = parts.getOrNull(1) ?: ""
                com.thecode007.turboxpress.dto.CustomerProfileResponseDto(
                    firstName = firstName,
                    lastName = lastName,
                    profilePicUrl = profile.profilePictureUrl
                )
            }
            "COURIER" -> {
                val profile = driverProfileRepository.findById(userId).orElseThrow { Exception("Driver profile not found") }
                val parts = profile.displayName?.split(" ", limit = 2) ?: listOf("", "")
                val firstName = parts.getOrNull(0) ?: ""
                val lastName = parts.getOrNull(1) ?: ""
                com.thecode007.turboxpress.dto.DriverProfileResponseDto(
                    firstName = firstName,
                    lastName = lastName,
                    nationality = "Lebanese", // Defaulting to Lebanese as requested by app design previously
                    profilePicUrl = profile.profilePictureUrl,
                    document1Url = profile.idDocumentUrl,
                    document2Url = profile.criminalRecordUrl
                )
            }
            "MERCHANT" -> {
                val profile = ownerProfileRepository.findById(userId).orElseThrow { Exception("Owner profile not found") }
                com.thecode007.turboxpress.dto.OwnerProfileResponseDto(
                    businessName = profile.businessName ?: "",
                    profilePicUrl = profile.profilePictureUrl
                )
            }
            else -> throw IllegalArgumentException("Invalid role")
        }
    }

    @Transactional
    fun uploadDocuments(
        userId: UUID,
        role: String,
        firstName: String,
        lastName: String,
        profilePic: MultipartFile?,
        doc1: MultipartFile?,
        doc2: MultipartFile?,
        additionalData: Map<String, String>
    ) {
        val normalizedRole = role.uppercase()
        val displayName = "$firstName $lastName"

        logger.info("Processing $normalizedRole profile update for user $userId")

        when (normalizedRole) {
            "CUSTOMER" -> {
                val profile = customerProfileRepository.findById(userId).orElseThrow { Exception("Customer profile not found") }
                profile.displayName = displayName
                
                profilePic?.let {
                    logger.info("Uploading profile picture for customer $userId")
                    val url = mediaService.uploadProfilePicture(it)
                    profile.profilePictureUrl = url
                }

                // Save the location label sent from the mobile sign-up form
                additionalData["location"]?.takeIf { it.isNotBlank() }?.let { loc ->
                    logger.info("Saving location label for customer $userId: $loc")
                    profile.defaultAddressLabel = loc
                }

                customerProfileRepository.save(profile)
                logger.info("Customer profile updated for $userId")
            }
            "COURIER" -> {
                val profile = driverProfileRepository.findById(userId).orElseThrow { Exception("Driver profile not found") }
                profile.displayName = displayName
                
                profilePic?.let {
                    logger.info("Uploading profile picture for driver $userId")
                    val url = mediaService.uploadProfilePicture(it)
                    profile.profilePictureUrl = url
                }
                
                doc1?.let {
                    logger.info("Uploading ID document for driver $userId")
                    val url = mediaService.uploadProfilePicture(it)
                    profile.idDocumentUrl = url
                }
                
                doc2?.let {
                    logger.info("Uploading criminal record document for driver $userId")
                    val url = mediaService.uploadProfilePicture(it)
                    profile.criminalRecordUrl = url
                }
                
                profile.verificationStatus = VerificationStatus.PENDING
                driverProfileRepository.save(profile)
                logger.info("Driver profile submitted for verification for $userId")
            }
            "MERCHANT" -> {
                val profile = ownerProfileRepository.findById(userId).orElseThrow { Exception("Owner profile not found") }
                profile.businessName = additionalData["restaurantName"] ?: displayName
                profile.locationDescription = additionalData["locationDescription"]

                // Save the coordinate/address string picked from the map
                additionalData["location"]?.takeIf { it.isNotBlank() }?.let { loc ->
                    logger.info("Saving restaurant location for merchant $userId: $loc")
                    profile.restaurantLocation = loc
                }
                
                profilePic?.let {
                    logger.info("Uploading profile picture for merchant $userId")
                    val url = mediaService.uploadProfilePicture(it)
                    profile.profilePictureUrl = url
                }
                
                doc1?.let {
                    logger.info("Uploading business ID document for merchant $userId")
                    val url = mediaService.uploadProfilePicture(it)
                    profile.idDocumentUrl = url
                }
                
                doc2?.let {
                    logger.info("Uploading business license document for merchant $userId")
                    val url = mediaService.uploadProfilePicture(it)
                    profile.criminalRecordUrl = url
                }
                
                profile.verificationStatus = VerificationStatus.PENDING
                ownerProfileRepository.save(profile)
                logger.info("Merchant profile submitted for verification for $userId")
            }
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    fun getOnlineStatus(userId: UUID): String {
        return driverProfileRepository.findById(userId)
            .map { it.onlineStatus.name }
            .orElse("OFFLINE")
    }

    @Transactional
    fun updateOnlineStatus(userId: UUID, status: String) {
        val profile = driverProfileRepository.findById(userId)
            .orElseThrow { Exception("Driver profile not found") }
        profile.onlineStatus = com.thecode007.turboxpress.entity.OnlineStatus.valueOf(status.uppercase())
        driverProfileRepository.save(profile)
        logger.info("Updated online status to $status for driver $userId")

        if (status.uppercase() == "ONLINE") {
            orderService.broadcastNextPendingOrder()
        }
    }
}
