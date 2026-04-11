package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, Long>
