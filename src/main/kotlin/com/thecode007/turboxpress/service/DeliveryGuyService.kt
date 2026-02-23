package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateDeliveryGuyRequest
import com.thecode007.turboxpress.dto.DeliveryGuyResponse
import com.thecode007.turboxpress.dto.PageResponse
import com.thecode007.turboxpress.dto.UpdateDeliveryGuyRequest
import com.thecode007.turboxpress.entity.DeliveryGuy
import com.thecode007.turboxpress.exception.UserNotFoundException
import com.thecode007.turboxpress.repository.DeliveryGuyRepository
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class DeliveryGuyService(
    private val deliveryGuyRepository: DeliveryGuyRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mediaService: MediaService
) {

    fun getAllDeliveryGuys(pageable: Pageable): PageResponse<DeliveryGuyResponse> {
        val page = deliveryGuyRepository.findAll(pageable)
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

    fun getDeliveryGuyByPhone(phoneNumber: String): DeliveryGuyResponse {
        val deliveryGuy = deliveryGuyRepository.findById(phoneNumber)
            .orElseThrow { UserNotFoundException("Delivery guy not found with phone: $phoneNumber") }
        return deliveryGuy.toResponse()
    }
    
    fun createDeliveryGuy(request: CreateDeliveryGuyRequest): DeliveryGuyResponse {
        // Check if phone number already exists
        if (deliveryGuyRepository.existsById(request.phoneNumber)) {
            throw IllegalArgumentException("Delivery guy with phone number ${request.phoneNumber} already exists")
        }
        
        // Check if username already exists
        if (deliveryGuyRepository.existsByUsername(request.username)) {
            throw IllegalArgumentException("Username ${request.username} already exists")
        }
        
        val deliveryGuy = DeliveryGuy(
            phoneNumber = request.phoneNumber,
            username = request.username,
            fullName = request.fullName,
            passwordHash = passwordEncoder.encode(request.password)?:"",
            profilePictureUrl = request.profilePictureUrl,
            isActive = true
        )
        
        val saved = deliveryGuyRepository.save(deliveryGuy)
        return saved.toResponse()
    }
    
    fun updateDeliveryGuy(phoneNumber: String, request: UpdateDeliveryGuyRequest): DeliveryGuyResponse {
        val deliveryGuy = deliveryGuyRepository.findById(phoneNumber)
            .orElseThrow { UserNotFoundException("Delivery guy not found with phone: $phoneNumber") }
        
        // Check if username is being changed and if it's already taken
        if (deliveryGuy.username != request.username && 
            deliveryGuyRepository.existsByUsername(request.username)) {
            throw IllegalArgumentException("Username ${request.username} already exists")
        }
        
        // Delete old profile picture if changing
        if (request.profilePictureUrl != deliveryGuy.profilePictureUrl && 
            !deliveryGuy.profilePictureUrl.isNullOrBlank()) {
            mediaService.deleteProfilePicture(deliveryGuy.profilePictureUrl!!)
        }
        
        deliveryGuy.username = request.username
        deliveryGuy.fullName = request.fullName
        deliveryGuy.profilePictureUrl = request.profilePictureUrl
        deliveryGuy.isActive = request.isActive
        
        val updated = deliveryGuyRepository.save(deliveryGuy)
        return updated.toResponse()
    }
    
    fun deleteDeliveryGuy(phoneNumber: String) {
        val deliveryGuy = deliveryGuyRepository.findById(phoneNumber)
            .orElseThrow { UserNotFoundException("Delivery guy not found with phone: $phoneNumber") }
        
        // Delete profile picture if exists
        if (!deliveryGuy.profilePictureUrl.isNullOrBlank()) {
            mediaService.deleteProfilePicture(deliveryGuy.profilePictureUrl!!)
        }
        
        deliveryGuyRepository.deleteById(phoneNumber)
    }

    private fun DeliveryGuy.toResponse(): DeliveryGuyResponse {
        return DeliveryGuyResponse(
            phoneNumber = this.phoneNumber,
            username = this.username,
            fullName = this.fullName,
            profilePictureUrl = this.profilePictureUrl,
            isActive = this.isActive,
            createdAt = this.createdAt
        )
    }
}
