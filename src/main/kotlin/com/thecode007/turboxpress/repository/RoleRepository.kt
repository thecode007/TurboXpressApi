package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RoleRepository : JpaRepository<Role, Int> {
    fun findByRoleName(roleName: String): Optional<Role>
}
