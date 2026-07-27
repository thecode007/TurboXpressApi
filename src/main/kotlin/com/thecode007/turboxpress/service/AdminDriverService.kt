package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateDriverRequest
import com.thecode007.turboxpress.dto.DriverResponse
import com.thecode007.turboxpress.dto.PageResponse
import com.thecode007.turboxpress.dto.UpdateDriverRequest
import com.thecode007.turboxpress.entity.DriverProfile
import com.thecode007.turboxpress.entity.User
import com.thecode007.turboxpress.entity.VerificationStatus
import com.thecode007.turboxpress.exception.ResourceNotFoundException
import com.thecode007.turboxpress.repository.DriverProfileRepository
import com.thecode007.turboxpress.repository.RoleRepository
import com.thecode007.turboxpress.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class AdminDriverService(
    private val userRepository: UserRepository,
    private val driverProfileRepository: DriverProfileRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional(readOnly = true)
    fun getAllActiveDrivers(pageable: Pageable, search: String?): PageResponse<DriverResponse> {
        // Fetch all driver profiles. For simplicity, we just filter by APPROVED status.
        val profiles = driverProfileRepository.findAllByVerificationStatus(VerificationStatus.APPROVED)
        
        // Filter by search if provided
        val filtered = if (!search.isNullOrBlank()) {
            profiles.filter {
                it.user.phoneNumber.contains(search, ignoreCase = true) ||
                (it.user.fullName.contains(search, ignoreCase = true)) ||
                (it.displayName?.contains(search, ignoreCase = true) == true)
            }
        } else {
            profiles
        }

        // Manual pagination
        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(filtered.size)
        val end = (start + pageable.pageSize).coerceAtMost(filtered.size)
        val pageList = filtered.subList(start, end)
        val page = PageImpl(pageList, pageable, filtered.size.toLong())

        val responses = page.content.map { it.toDriverResponse() }

        return PageResponse(
            content = responses,
            pageNumber = page.number,
            pageSize = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            isLast = page.isLast
        )
    }

    @Transactional(readOnly = true)
    fun getAvailableDrivers(search: String?): List<DriverResponse> {
        val profiles = driverProfileRepository.findAllByVerificationStatusAndOnlineStatusAndStatus(
            VerificationStatus.APPROVED,
            com.thecode007.turboxpress.entity.OnlineStatus.ONLINE,
            com.thecode007.turboxpress.entity.DriverStatus.IDLE
        )

        val filtered = if (!search.isNullOrBlank()) {
            profiles.filter {
                it.user.phoneNumber.contains(search, ignoreCase = true) ||
                (it.user.fullName.contains(search, ignoreCase = true)) ||
                (it.displayName?.contains(search, ignoreCase = true) == true)
            }
        } else {
            profiles
        }

        return filtered.map { it.toDriverResponse() }
    }

    @Transactional(readOnly = true)
    fun getDriverByPhone(phoneNumber: String): DriverResponse {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { ResourceNotFoundException("Driver not found with phone: $phoneNumber") }
        val profile = driverProfileRepository.findByUserId(user.id!!)
            .orElseThrow { ResourceNotFoundException("Driver profile not found") }
        return profile.toDriverResponse()
    }

    @Transactional
    fun createDriver(request: CreateDriverRequest): DriverResponse {
        if (userRepository.findByPhoneNumber(request.phoneNumber).isPresent) {
            throw IllegalArgumentException("Phone number already registered")
        }

        val role = roleRepository.findByRoleName("COURIER")
            .orElseThrow { ResourceNotFoundException("COURIER role not found") }

        val user = User(
            username = request.username,
            fullName = request.fullName,
            phoneNumber = request.phoneNumber,
            passwordHash = passwordEncoder.encode(request.password) ?: "",
            isActive = true,
            roles = mutableSetOf(role)
        )
        val savedUser = userRepository.save(user)

        val profile = DriverProfile(
            userId = savedUser.id!!,
            user = savedUser,
            displayName = request.fullName,
            profilePictureUrl = request.profilePictureUrl,
            dailyRate = BigDecimal.valueOf(request.dailyRate),
            verificationStatus = VerificationStatus.APPROVED
        )
        val savedProfile = driverProfileRepository.save(profile)

        return savedProfile.toDriverResponse()
    }

    @Transactional
    fun updateDriver(phoneNumber: String, request: UpdateDriverRequest): DriverResponse {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { ResourceNotFoundException("Driver not found") }
        
        user.username = request.username
        user.fullName = request.fullName
        user.isActive = request.isActive
        userRepository.save(user)

        val profile = driverProfileRepository.findByUserId(user.id!!)
            .orElseThrow { ResourceNotFoundException("Driver profile not found") }
        
        profile.displayName = request.fullName
        profile.profilePictureUrl = request.profilePictureUrl ?: profile.profilePictureUrl
        profile.dailyRate = BigDecimal.valueOf(request.dailyRate)
        val savedProfile = driverProfileRepository.save(profile)

        return savedProfile.toDriverResponse()
    }

    @Transactional
    fun deleteDriver(phoneNumber: String) {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { ResourceNotFoundException("Driver not found") }
        userRepository.delete(user)
    }

    @Transactional
    fun changeDriverPassword(phoneNumber: String, request: com.thecode007.turboxpress.dto.ChangeDriverPasswordRequest) {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { ResourceNotFoundException("Driver not found") }
        user.passwordHash = passwordEncoder.encode(request.newPassword) ?: ""
        userRepository.save(user)
    }

    private fun DriverProfile.toDriverResponse(): DriverResponse {
        return DriverResponse(
            phoneNumber = this.user.phoneNumber,
            username = this.user.username ?: this.user.phoneNumber,
            fullName = this.displayName ?: this.user.fullName,
            profilePictureUrl = this.profilePictureUrl,
            isActive = this.user.isActive,
            onlineStatus = this.onlineStatus.name,
            monthlySubFee = this.monthlySubFee,
            billingCycle = this.billingCycle.name,
            nextBillingDate = this.nextBillingDate?.toString(),
            createdAt = this.createdAt?.toString(),
            adminDebtBalance = this.adminDebtBalance.toDouble(),
            collectedCashBalance = this.collectedCashBalance.toDouble(),
            dailyRate = this.dailyRate.toDouble()
        )
    }
}
