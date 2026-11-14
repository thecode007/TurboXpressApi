package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateTerritoryRequest
import com.thecode007.turboxpress.dto.GeoPointDto
import com.thecode007.turboxpress.dto.TerritoryResponse
import com.thecode007.turboxpress.dto.UpdateTerritoryRequest
import com.thecode007.turboxpress.entity.Territory
import com.thecode007.turboxpress.exception.DuplicateResourceException
import com.thecode007.turboxpress.exception.ResourceNotFoundException
import com.thecode007.turboxpress.repository.RestaurantRepository
import com.thecode007.turboxpress.repository.TerritoryRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.io.WKTReader
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TerritoryService(
    private val territoryRepository: TerritoryRepository,
    private val restaurantRepository: RestaurantRepository
) {

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Cacheable("territories")
    @Transactional(readOnly = true)
    fun getAllTerritories(): List<TerritoryResponse> {
        return territoryRepository.findAll().map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getTerritoryById(id: Long): TerritoryResponse {
        val territory = territoryRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Territory not found with id: $id") }
        return territory.toResponse()
    }

    @Transactional(readOnly = true)
    fun resolveTerritoryForPoint(point: Point): Territory? {
        return territoryRepository.findActiveTerritoriesContainingPoint(point).firstOrNull()
    }

    @Transactional(readOnly = true)
    fun resolveTerritoryForCoordinates(latitude: Double, longitude: Double): Territory? {
        val point = geometryFactory.createPoint(Coordinate(longitude, latitude))
        return resolveTerritoryForPoint(point)
    }

    @Transactional(readOnly = true)
    fun resolveTerritoryResponseForCoordinates(latitude: Double, longitude: Double): TerritoryResponse? {
        return resolveTerritoryForCoordinates(latitude, longitude)?.toResponse()
    }

    @Caching(
        evict = [
            CacheEvict(value = ["territories"], allEntries = true),
            CacheEvict(value = ["restaurants"], allEntries = true)
        ]
    )
    @Transactional
    fun createTerritory(request: CreateTerritoryRequest): TerritoryResponse {
        if (territoryRepository.existsByName(request.name)) {
            throw DuplicateResourceException("Territory with name '${request.name}' already exists")
        }

        val polygon = parsePolygonWkt(request.polygonWkt)

        val territory = Territory(
            name = request.name,
            isActive = true,
            polygon = polygon
        )

        val saved = territoryRepository.saveAndFlush(territory)
        recalculateAllRestaurantTerritories()
        return saved.toResponse()
    }

    @Caching(
        evict = [
            CacheEvict(value = ["territories"], allEntries = true),
            CacheEvict(value = ["restaurants"], allEntries = true)
        ]
    )
    @Transactional
    fun updateTerritory(id: Long, request: UpdateTerritoryRequest): TerritoryResponse {
        val territory = territoryRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Territory not found with id: $id") }

        if (territoryRepository.existsByNameAndIdNot(request.name, id)) {
            throw DuplicateResourceException("Territory with name '${request.name}' already exists")
        }

        val polygon = parsePolygonWkt(request.polygonWkt)

        territory.name = request.name
        territory.isActive = request.isActive
        territory.polygon = polygon

        val saved = territoryRepository.saveAndFlush(territory)
        recalculateAllRestaurantTerritories()
        return saved.toResponse()
    }

    @Caching(
        evict = [
            CacheEvict(value = ["territories"], allEntries = true),
            CacheEvict(value = ["restaurants"], allEntries = true)
        ]
    )
    @Transactional
    fun deleteTerritory(id: Long) {
        if (!territoryRepository.existsById(id)) {
            throw ResourceNotFoundException("Territory not found with id: $id")
        }
        territoryRepository.deleteById(id)
        territoryRepository.flush()
        recalculateAllRestaurantTerritories()
    }

    /**
     * Re-evaluates all restaurants' locations against currently active territory polygons
     * so restaurants remain accurately assigned when territories are added, edited, or removed.
     */
    @Transactional
    fun recalculateAllRestaurantTerritories() {
        val restaurants = restaurantRepository.findAll()
        if (restaurants.isEmpty()) return

        var changed = false
        for (restaurant in restaurants) {
            val matchedTerritory = resolveTerritoryForPoint(restaurant.location)
            if (restaurant.territory?.id != matchedTerritory?.id) {
                restaurant.territory = matchedTerritory
                changed = true
            }
        }
        if (changed) {
            restaurantRepository.saveAll(restaurants)
        }
    }

    private fun parsePolygonWkt(wkt: String): Polygon {
        try {
            val reader = WKTReader(geometryFactory)
            val geometry = reader.read(wkt)
            if (geometry is Polygon) {
                if (!geometry.isValid) {
                    throw IllegalArgumentException("Provided Polygon WKT is geometrically invalid")
                }
                return geometry
            } else {
                throw IllegalArgumentException("Provided WKT is not a Polygon")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse WKT Polygon: ${e.message}", e)
        }
    }

    private fun Territory.toResponse(): TerritoryResponse {
        val points = this.polygon?.coordinates?.map { coord ->
            // JTS uses (x, y) which corresponds to (longitude, latitude) in SRID 4326
            GeoPointDto(lat = coord.y, lng = coord.x)
        } ?: emptyList()

        return TerritoryResponse(
            id = this.id,
            name = this.name,
            isActive = this.isActive,
            polygon = points
        )
    }
}
