package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateDeliveryZoneRequest
import com.thecode007.turboxpress.dto.DeliveryZoneResponse
import com.thecode007.turboxpress.dto.UpdateDeliveryZoneRequest
import com.thecode007.turboxpress.entity.DeliveryZone
import com.thecode007.turboxpress.repository.DeliveryZoneRepository
import jakarta.annotation.PostConstruct
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.operation.valid.IsValidOp
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class DeliveryZoneService(
    private val deliveryZoneRepository: DeliveryZoneRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(DeliveryZoneService::class.java)
    private val geometryFactory = GeometryFactory(org.locationtech.jts.geom.PrecisionModel(), 4326)

    @PostConstruct
    fun syncSequence() {
        try {
            jdbcTemplate.execute(
                """
                SELECT setval(
                    pg_get_serial_sequence('delivery_zones', 'id'),
                    COALESCE((SELECT MAX(id) FROM delivery_zones), 1),
                    (SELECT MAX(id) IS NOT NULL FROM delivery_zones)
                )
                """.trimIndent()
            )
            log.info("Synchronized delivery_zones_id_seq with current MAX(id)")
        } catch (e: Exception) {
            log.warn("Could not synchronize delivery_zones_id_seq: {}", e.message)
        }
    }

    fun isLocationInZone(lat: Double, lng: Double): Boolean {
        val point = geometryFactory.createPoint(Coordinate(lng, lat))
        val zones = deliveryZoneRepository.findActiveZonesContainingPoint(point)
        return zones.isNotEmpty()
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @Cacheable(value = ["zones"])
    fun getAllDeliveryZones(): List<DeliveryZoneResponse> {
        return deliveryZoneRepository.findAll().map { DeliveryZoneResponse.from(it) }
    }

    @CacheEvict(value = ["zones"], allEntries = true)
    fun createDeliveryZone(request: CreateDeliveryZoneRequest): DeliveryZoneResponse {
        log.info("Creating delivery zone: name='{}', isActive={}, wktPolygon='{}'",
            request.name, request.isActive, request.wktPolygon)

        if (deliveryZoneRepository.existsByName(request.name)) {
            log.warn("Cannot create delivery zone: name '{}' already exists", request.name)
            throw IllegalArgumentException("A delivery zone with name '${request.name}' already exists")
        }

        val polygon = parseWkt(request.wktPolygon)
        logGeometryDetails("Create zone '${request.name}'", polygon)

        // Ensure sequence is aligned with MAX(id) to prevent duplicate key constraint violations
        syncSequence()

        val zone = DeliveryZone(
            name = request.name,
            isActive = request.isActive,
            polygon = polygon
        )

        val savedZone = try {
            deliveryZoneRepository.save(zone)
        } catch (e: Exception) {
            log.error("Failed to persist delivery zone '{}' to database: {}", request.name, e.message, e)
            throw e
        }

        log.info("Successfully created delivery zone id={}, name='{}', verticesCount={}",
            savedZone.id, savedZone.name, savedZone.polygon.numPoints)
        return DeliveryZoneResponse.from(savedZone)
    }

    @CacheEvict(value = ["zones"], allEntries = true)
    fun updateDeliveryZone(id: Long, request: UpdateDeliveryZoneRequest): DeliveryZoneResponse {
        log.info("Updating delivery zone id={}: name='{}', isActive={}, wktPolygon='{}'",
            id, request.name, request.isActive, request.wktPolygon)

        val zone = deliveryZoneRepository.findById(id)
            .orElseThrow {
                log.warn("Cannot update delivery zone: zone not found with id: {}", id)
                IllegalArgumentException("Delivery zone not found with id: $id")
            }

        if (request.name != null && deliveryZoneRepository.existsByNameAndIdNot(request.name, id)) {
            log.warn("Cannot update delivery zone id={}: name '{}' already exists for another zone", id, request.name)
            throw IllegalArgumentException("A delivery zone with name '${request.name}' already exists")
        }

        request.name?.let { zone.name = it }
        request.isActive?.let { zone.isActive = it }
        request.wktPolygon?.let {
            val polygon = parseWkt(it)
            logGeometryDetails("Update zone id=$id", polygon)
            zone.polygon = polygon
        }

        val savedZone = try {
            deliveryZoneRepository.save(zone)
        } catch (e: Exception) {
            log.error("Failed to update delivery zone id={} in database: {}", id, e.message, e)
            throw e
        }

        log.info("Successfully updated delivery zone id={}, name='{}'", savedZone.id, savedZone.name)
        return DeliveryZoneResponse.from(savedZone)
    }

    @CacheEvict(value = ["zones"], allEntries = true)
    fun deleteDeliveryZone(id: Long) {
        log.info("Deleting delivery zone id={}", id)
        if (!deliveryZoneRepository.existsById(id)) {
            log.warn("Cannot delete delivery zone: not found with id: {}", id)
            throw IllegalArgumentException("Delivery zone not found with id: $id")
        }
        deliveryZoneRepository.deleteById(id)
        log.info("Successfully deleted delivery zone id={}", id)
    }

    private fun parseWkt(wkt: String): org.locationtech.jts.geom.Polygon {
        log.debug("Parsing WKT polygon: {}", wkt)
        return try {
            val geom = WKTReader(geometryFactory).read(wkt)
            if (geom !is org.locationtech.jts.geom.Polygon) {
                log.error("Parsed geometry is not a Polygon. Found type: '{}', WKT: '{}'", geom.geometryType, wkt)
                throw IllegalArgumentException("Provided geometry is not a Polygon: ${geom.geometryType}")
            }
            if (geom.srid == 0) {
                geom.srid = 4326
            }
            geom
        } catch (e: Exception) {
            log.error("Failed to parse WKT Polygon from input '{}': {}", wkt, e.message, e)
            throw IllegalArgumentException("Invalid WKT Polygon: ${e.message}")
        }
    }

    private fun logGeometryDetails(context: String, polygon: org.locationtech.jts.geom.Polygon) {
        val isValid = polygon.isValid
        val centroid = polygon.centroid
        val numPoints = polygon.numPoints
        val envelope = polygon.envelopeInternal
        val area = polygon.area
        val coordsSummary = polygon.coordinates.joinToString(", ") { "(${it.y}, ${it.x})" }

        if (isValid) {
            log.info(
                "[{}] Polygon details: valid=true, srid={}, vertices={}, centroid=(lat={}, lng={}), area={}, bbox=[minX={}, minY={}, maxX={}, maxY={}], coords=[{}]",
                context, polygon.srid, numPoints, centroid.y, centroid.x, area,
                envelope.minX, envelope.minY, envelope.maxX, envelope.maxY,
                coordsSummary
            )
        } else {
            val validOp = IsValidOp(polygon)
            val err = validOp.validationError
            log.warn(
                "[{}] Polygon details: valid=FALSE (Topology Error: {} at coordinate {}), srid={}, vertices={}, centroid=(lat={}, lng={}), bbox=[minX={}, minY={}, maxX={}, maxY={}], coords=[{}]",
                context, err?.message, err?.coordinate, polygon.srid, numPoints, centroid.y, centroid.x,
                envelope.minX, envelope.minY, envelope.maxX, envelope.maxY,
                coordsSummary
            )
        }
    }
}
