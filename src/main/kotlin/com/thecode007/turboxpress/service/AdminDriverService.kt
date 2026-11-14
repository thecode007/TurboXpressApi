package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateDriverRequest
import com.thecode007.turboxpress.dto.DriverResponse
import com.thecode007.turboxpress.dto.PageResponse
import com.thecode007.turboxpress.dto.UpdateDriverRequest
import com.thecode007.turboxpress.entity.DriverProfile
import com.thecode007.turboxpress.entity.OrderStatus
import com.thecode007.turboxpress.entity.User
import com.thecode007.turboxpress.entity.VerificationStatus
import com.thecode007.turboxpress.exception.ResourceNotFoundException
import com.thecode007.turboxpress.repository.DriverProfileRepository
import com.thecode007.turboxpress.repository.OrderRepository
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
    private val territoryRepository: com.thecode007.turboxpress.repository.TerritoryRepository,
    private val passwordEncoder: PasswordEncoder,
    private val driverLocationService: DriverLocationService,
    private val driverOnlineTrackingService: DriverOnlineTrackingService,
    private val orderRepository: OrderRepository,
    @org.springframework.beans.factory.annotation.Value("\${driver.online.stale-threshold-seconds:45}")
    private val staleThresholdSeconds: Long = 45
) {

    companion object {
        private val ACTIVE_ORDER_STATUSES = listOf(
            OrderStatus.ACCEPTED,
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.ON_THE_WAY
        )
    }

    private fun onlineCutoff(): java.time.Instant =
        java.time.Instant.now().minusSeconds(staleThresholdSeconds)

    private fun getTodayShiftWindow(): Pair<java.time.Instant, java.time.Instant> {
        val zoneId = java.time.ZoneId.systemDefault()
        val nowZoned = java.time.Instant.now().atZone(zoneId)
        val currentBusinessDay = if (nowZoned.hour < 9) nowZoned.toLocalDate().minusDays(1) else nowZoned.toLocalDate()
        val start = currentBusinessDay.atTime(9, 0).atZone(zoneId).toInstant()
        val end = currentBusinessDay.plusDays(1).atTime(2, 0).atZone(zoneId).toInstant()
        return Pair(start, end)
    }

    @Transactional(readOnly = true)
    fun getAllActiveDrivers(pageable: Pageable, search: String?, territoryId: Long? = null): PageResponse<DriverResponse> {
        // Fetch all driver profiles. For simplicity, we just filter by APPROVED status.
        val profiles = driverProfileRepository.findAllByVerificationStatus(VerificationStatus.APPROVED)
        
        // Filter by territory if specified (e.g. territory-scoped admin)
        val territoryFiltered = if (territoryId != null) {
            profiles.filter { (it.territory?.id ?: it.user.territory?.id) == territoryId }
        } else {
            profiles
        }

        // Filter by search if provided
        val filtered = if (!search.isNullOrBlank()) {
            territoryFiltered.filter {
                it.user.phoneNumber.contains(search, ignoreCase = true) ||
                (it.user.fullName.contains(search, ignoreCase = true)) ||
                (it.displayName?.contains(search, ignoreCase = true) == true)
            }
        } else {
            territoryFiltered
        }

        // Manual pagination
        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(filtered.size)
        val end = (start + pageable.pageSize).coerceAtMost(filtered.size)
        val pageList = filtered.subList(start, end)
        val page = PageImpl(pageList, pageable, filtered.size.toLong())

        val pagedDriverIds = pageList.map { it.userId }
        val orderCounts = if (pagedDriverIds.isEmpty()) {
            emptyMap()
        } else {
            orderRepository.countActiveOrdersByDriverIds(pagedDriverIds, ACTIVE_ORDER_STATUSES)
                .mapNotNull { row ->
                    val driverId = row.getOrNull(0) as? java.util.UUID ?: return@mapNotNull null
                    val count = (row.getOrNull(1) as? Number)?.toInt() ?: 0
                    driverId to count
                }
                .toMap()
        }

        val (shiftStart, shiftEnd) = getTodayShiftWindow()
        val onlineDurations = driverOnlineTrackingService.calculateOnlineDurationForDrivers(pagedDriverIds, shiftStart, shiftEnd)

        val responses = pageList.map {
            it.toDriverResponse(
                orderCount = orderCounts[it.userId] ?: 0,
                onlineDurationSeconds = onlineDurations[it.userId] ?: 0L
            )
        }

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
    fun getAvailableDrivers(search: String?, territoryId: Long? = null): List<DriverResponse> {
        val profiles = driverProfileRepository.findAllApprovedOnlineDriversWithUser(onlineCutoff())

        val territoryFiltered = if (territoryId != null) {
            profiles.filter { (it.territory?.id ?: it.user.territory?.id) == territoryId }
        } else {
            profiles
        }

        val filtered = if (!search.isNullOrBlank()) {
            territoryFiltered.filter {
                it.user.phoneNumber.contains(search, ignoreCase = true) ||
                (it.user.fullName.contains(search, ignoreCase = true)) ||
                (it.displayName?.contains(search, ignoreCase = true) == true)
            }
        } else {
            territoryFiltered
        }

        val driverIds = filtered.map { it.userId }
        val orderCounts = if (driverIds.isEmpty()) {
            emptyMap()
        } else {
            orderRepository.countActiveOrdersByDriverIds(driverIds, ACTIVE_ORDER_STATUSES)
                .mapNotNull { row ->
                    val driverId = row.getOrNull(0) as? java.util.UUID ?: return@mapNotNull null
                    val count = (row.getOrNull(1) as? Number)?.toInt() ?: 0
                    driverId to count
                }
                .toMap()
        }

        val (shiftStart, shiftEnd) = getTodayShiftWindow()
        val onlineDurations = driverOnlineTrackingService.calculateOnlineDurationForDrivers(driverIds, shiftStart, shiftEnd)

        return filtered.map {
            it.toDriverResponse(
                orderCount = orderCounts[it.userId] ?: 0,
                onlineDurationSeconds = onlineDurations[it.userId] ?: 0L
            )
        }
    }

    @Transactional(readOnly = true)
    fun getDriverByPhone(phoneNumber: String): DriverResponse {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { ResourceNotFoundException("Driver not found with phone: $phoneNumber") }
        val profile = driverProfileRepository.findByUserId(user.id!!)
            .orElseThrow { ResourceNotFoundException("Driver profile not found") }
        val activeOrderCount = orderRepository.countByDriverIdAndStatusIn(profile.userId, ACTIVE_ORDER_STATUSES).toInt()
        val (shiftStart, shiftEnd) = getTodayShiftWindow()
        val onlineSec = driverOnlineTrackingService.calculateOnlineDurationSeconds(profile.userId, shiftStart, shiftEnd)
        return profile.toDriverResponse(orderCount = activeOrderCount, onlineDurationSeconds = onlineSec)
    }

    @Transactional
    fun createDriver(request: CreateDriverRequest): DriverResponse {
        if (userRepository.findByPhoneNumber(request.phoneNumber).isPresent) {
            throw IllegalArgumentException("Phone number already registered")
        }

        val role = roleRepository.findByRoleName("COURIER")
            .orElseThrow { ResourceNotFoundException("COURIER role not found") }

        val territory = request.territoryId?.let { id ->
            territoryRepository.findById(id)
                .orElseThrow { ResourceNotFoundException("Territory not found with id: $id") }
        }

        val user = User(
            username = request.username,
            fullName = request.fullName,
            phoneNumber = request.phoneNumber,
            passwordHash = passwordEncoder.encode(request.password) ?: "",
            isActive = true,
            roles = mutableSetOf(role),
            territory = territory
        )
        val savedUser = userRepository.save(user)

        val profile = DriverProfile(
            userId = savedUser.id!!,
            user = savedUser,
            displayName = request.fullName,
            profilePictureUrl = request.profilePictureUrl,
            dailyRate = BigDecimal.valueOf(request.dailyRate),
            verificationStatus = VerificationStatus.APPROVED,
            territory = territory
        )
        val savedProfile = driverProfileRepository.save(profile)

        return savedProfile.toDriverResponse()
    }

    @Transactional
    fun updateDriver(phoneNumber: String, request: UpdateDriverRequest): DriverResponse {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { ResourceNotFoundException("Driver not found") }

        val territory = request.territoryId?.let { id ->
            territoryRepository.findById(id)
                .orElseThrow { ResourceNotFoundException("Territory not found with id: $id") }
        }
        
        user.username = request.username
        user.fullName = request.fullName
        user.isActive = request.isActive
        user.territory = territory
        userRepository.save(user)

        val profile = driverProfileRepository.findByUserId(user.id!!)
            .orElseThrow { ResourceNotFoundException("Driver profile not found") }
        
        profile.displayName = request.fullName
        profile.profilePictureUrl = request.profilePictureUrl ?: profile.profilePictureUrl
        profile.dailyRate = BigDecimal.valueOf(request.dailyRate)
        profile.territory = territory
        if (!request.isActive) {
            profile.goOffline()
            driverLocationService.evictPresence(user.id!!)
            driverOnlineTrackingService.onDriverGoOffline(user.id!!)
        }
        val savedProfile = driverProfileRepository.save(profile)

        val (shiftStart, shiftEnd) = getTodayShiftWindow()
        val onlineSec = driverOnlineTrackingService.calculateOnlineDurationSeconds(profile.userId, shiftStart, shiftEnd)
        return savedProfile.toDriverResponse(onlineDurationSeconds = onlineSec)
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

    private fun DriverProfile.toDriverResponse(orderCount: Int = 0, onlineDurationSeconds: Long = 0L): DriverResponse {
        val effectiveStatus = if (this.isEffectivelyOnline(onlineCutoff())) {
            com.thecode007.turboxpress.entity.OnlineStatus.ONLINE.name
        } else {
            com.thecode007.turboxpress.entity.OnlineStatus.OFFLINE.name
        }
        val onlineMins = onlineDurationSeconds / 60
        val formatted = driverOnlineTrackingService.formatDuration(onlineDurationSeconds)
        val resolvedTerritory = this.territory ?: this.user.territory
        return DriverResponse(
            phoneNumber = this.user.phoneNumber,
            username = this.user.username ?: this.user.phoneNumber,
            fullName = this.displayName ?: this.user.fullName,
            profilePictureUrl = this.profilePictureUrl,
            isActive = this.user.isActive,
            onlineStatus = effectiveStatus,
            monthlySubFee = this.monthlySubFee,
            billingCycle = this.billingCycle.name,
            nextBillingDate = this.nextBillingDate?.toString(),
            createdAt = this.createdAt?.toString(),
            adminDebtBalance = this.adminDebtBalance.toDouble(),
            collectedCashBalance = this.collectedCashBalance.toDouble(),
            dailyRate = this.dailyRate.toDouble(),
            orderCount = orderCount,
            activeOrderCount = orderCount,
            todayOnlineMinutes = onlineMins,
            todayOnlineHoursFormatted = formatted,
            latitude = this.currentLocation?.y,
            longitude = this.currentLocation?.x,
            territoryId = resolvedTerritory?.id,
            territoryName = resolvedTerritory?.name
        )
    }
}
