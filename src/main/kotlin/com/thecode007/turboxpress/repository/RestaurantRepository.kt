package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.Restaurant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface RestaurantRepository : JpaRepository<Restaurant, Long> {
    fun existsByName(name: String): Boolean
    fun existsByNameAndIdNot(name: String, id: Long): Boolean
}
