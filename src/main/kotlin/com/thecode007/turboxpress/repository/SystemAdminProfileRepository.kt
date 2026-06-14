package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.SystemAdminProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface SystemAdminProfileRepository : JpaRepository<SystemAdminProfile, UUID> {
    fun findByUserId(userId: UUID): Optional<SystemAdminProfile>
    fun existsByUserId(userId: UUID): Boolean
}
