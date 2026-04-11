package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.DeliveryGuy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface DeliveryGuyRepository : JpaRepository<DeliveryGuy, String> {
    fun findByUsername(username: String): Optional<DeliveryGuy>
    fun existsByUsername(username: String): Boolean

    @Modifying
    @Transactional
    @Query("UPDATE delivery_guys d SET d.admin_debt_balance = d.admin_debt_balance + d.daily_rate WHERE d.is_active = true", nativeQuery = true)
    fun accrueDailySalaries(): Int
}
