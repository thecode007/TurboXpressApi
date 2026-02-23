package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.DeliveryGuy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface DeliveryGuyRepository : JpaRepository<DeliveryGuy, String> {
    fun findByUsername(username: String): Optional<DeliveryGuy>
    fun existsByUsername(username: String): Boolean
}
