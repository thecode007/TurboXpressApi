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
    private val mediaService: MediaService
) {
    private val logger = LoggerFactory.getLogger(ProfileService::class.java)

    fun getStatus(userId: UUID, role: String): String {
        return when (role.uppercase()) {
            "CUSTOMER" -> customerProfileRepository.findById(userId).map { it.verificationStatus.name }.orElse("PENDING")
            "COURIER" -> driverProfileRepository.findById(userId).map { it.verificationStatus.name }.orElse("PENDING")
            "MERCHANT" -> ownerProfileRepository.findById(userId).map { it.verificationStatus.name }.orElse("PENDING")
            else -> "PENDING"
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
}
