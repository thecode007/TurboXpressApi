package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.Territory
import org.locationtech.jts.geom.Point
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TerritoryRepository : JpaRepository<Territory, Long> {

    @Query("SELECT t FROM Territory t WHERE t.isActive = true AND contains(t.polygon, :point) = true ORDER BY t.id ASC")
    fun findActiveTerritoriesContainingPoint(@Param("point") point: Point): List<Territory>

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: Long): Boolean
}
