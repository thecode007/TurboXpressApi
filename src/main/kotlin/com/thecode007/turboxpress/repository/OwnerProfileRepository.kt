package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.OwnerProfile
import com.thecode007.turboxpress.entity.VerificationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface OwnerProfileRepository : JpaRepository<OwnerProfile, UUID> {
    fun findByUserId(userId: UUID): Optional<OwnerProfile>
    fun existsByUserId(userId: UUID): Boolean
    fun findAllByVerificationStatus(status: VerificationStatus): List<OwnerProfile>

    /** Eagerly fetches the related User to avoid LazyInitializationException in service layer. */
    @Query("SELECT p FROM OwnerProfile p JOIN FETCH p.user WHERE p.userId = :userId")
    fun findByUserIdWithUser(userId: UUID): Optional<OwnerProfile>

    /** Eagerly fetches the related User for all profiles of a given status. */
    @Query("SELECT p FROM OwnerProfile p JOIN FETCH p.user WHERE p.verificationStatus = :status")
    fun findAllByVerificationStatusWithUser(status: VerificationStatus): List<OwnerProfile>
}
