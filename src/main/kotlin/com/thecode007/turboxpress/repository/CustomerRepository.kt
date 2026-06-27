package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.Customer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface CustomerRepository : JpaRepository<Customer, Long> {
    fun findByPhoneNumber(phone: String): Optional<Customer>
    fun existsByPhoneNumber(phone: String): Boolean
    fun findByUserId(userId: UUID): Optional<Customer>
}
