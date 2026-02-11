package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByPhoneNumber(phoneNumber: String): Optional<User>
    fun findByUsername(username: String): Optional<User>
    override fun findAll(pageable: Pageable): Page<User>
}
