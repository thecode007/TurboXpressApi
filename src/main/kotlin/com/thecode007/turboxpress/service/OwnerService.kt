package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateOwnerRequest
import com.thecode007.turboxpress.dto.OwnerResponse
import com.thecode007.turboxpress.dto.PageResponse
import com.thecode007.turboxpress.dto.UpdateOwnerRequest
import com.thecode007.turboxpress.entity.Owner
import com.thecode007.turboxpress.exception.UserNotFoundException
import com.thecode007.turboxpress.repository.OwnerRepository
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class OwnerService(
    private val ownerRepository: OwnerRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mediaService: MediaService
) {

    fun getAllOwners(pageable: Pageable): PageResponse<OwnerResponse> {
        val page = ownerRepository.findAll(pageable)
        val responses = page.content.map { it.toResponse() }
        
        return PageResponse(
            content = responses,
            pageNumber = page.number,
            pageSize = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            isLast = page.isLast
        )
    }

    fun getOwnerByPhone(phoneNumber: String): OwnerResponse {
        val owner = ownerRepository.findById(phoneNumber)
            .orElseThrow { UserNotFoundException("Owner not found with phone: $phoneNumber") }
        return owner.toResponse()
    }
    
    fun createOwner(request: CreateOwnerRequest): OwnerResponse {
        // Check if phone number already exists
        if (ownerRepository.existsById(request.phoneNumber)) {
            throw IllegalArgumentException("Owner with phone number ${request.phoneNumber} already exists")
        }
        
        val owner = Owner(
            phoneNumber = request.phoneNumber,
            fullName = request.fullName,
            passwordHash = passwordEncoder.encode(request.password) ?: "",
            profilePictureUrl = request.profilePictureUrl,
            isActive = true
        )
        
        val saved = ownerRepository.save(owner)
        return saved.toResponse()
    }
    
    fun updateOwner(phoneNumber: String, request: UpdateOwnerRequest): OwnerResponse {
        val owner = ownerRepository.findById(phoneNumber)
            .orElseThrow { UserNotFoundException("Owner not found with phone: $phoneNumber") }
        
        // Delete old profile picture if changing
        if (request.profilePictureUrl != owner.profilePictureUrl && 
            !owner.profilePictureUrl.isNullOrBlank()) {
            mediaService.deleteProfilePicture(owner.profilePictureUrl!!)
        }
        
        owner.fullName = request.fullName
        owner.profilePictureUrl = request.profilePictureUrl
        owner.isActive = request.isActive
        
        val updated = ownerRepository.save(owner)
        return updated.toResponse()
    }
    
    fun deleteOwner(phoneNumber: String) {
        val owner = ownerRepository.findById(phoneNumber)
            .orElseThrow { UserNotFoundException("Owner not found with phone: $phoneNumber") }
        
        // Delete profile picture if exists
        if (!owner.profilePictureUrl.isNullOrBlank()) {
            mediaService.deleteProfilePicture(owner.profilePictureUrl!!)
        }
        
        ownerRepository.deleteById(phoneNumber)
    }

    private fun Owner.toResponse(): OwnerResponse {
        return OwnerResponse(
            phoneNumber = this.phoneNumber,
            fullName = this.fullName,
            profilePictureUrl = this.profilePictureUrl,
            isActive = this.isActive,
            createdAt = this.createdAt
        )
    }
}
