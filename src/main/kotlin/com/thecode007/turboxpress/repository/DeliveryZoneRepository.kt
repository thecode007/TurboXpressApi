package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.DeliveryZone
import org.locationtech.jts.geom.Point
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeliveryZoneRepository : JpaRepository<DeliveryZone, Long> {

    @Query("SELECT z FROM DeliveryZone z WHERE z.isActive = true AND contains(z.polygon, :point) = true")
    fun findActiveZonesContainingPoint(@Param("point") point: Point): List<DeliveryZone>

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: Long): Boolean
}
