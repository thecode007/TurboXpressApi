package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.CustomerProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface CustomerProfileRepository : JpaRepository<CustomerProfile, UUID> {
    fun findByUserId(userId: UUID): Optional<CustomerProfile>
    fun existsByUserId(userId: UUID): Boolean
}
