package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateDeliveryZoneRequest
import com.thecode007.turboxpress.dto.DeliveryZoneResponse
import com.thecode007.turboxpress.dto.UpdateDeliveryZoneRequest
import com.thecode007.turboxpress.entity.DeliveryZone
import com.thecode007.turboxpress.repository.DeliveryZoneRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKTReader
import org.springframework.stereotype.Service

@Service
class DeliveryZoneService(
    private val deliveryZoneRepository: DeliveryZoneRepository
) {
    private val geometryFactory = GeometryFactory()

    fun isLocationInZone(lat: Double, lng: Double): Boolean {
        val point = geometryFactory.createPoint(Coordinate(lng, lat))
        val zones = deliveryZoneRepository.findActiveZonesContainingPoint(point)
        return zones.isNotEmpty()
    }

    fun getAllDeliveryZones(): List<DeliveryZoneResponse> {
        return deliveryZoneRepository.findAll().map { DeliveryZoneResponse.from(it) }
    }

    fun createDeliveryZone(request: CreateDeliveryZoneRequest): DeliveryZoneResponse {
        if (deliveryZoneRepository.existsByName(request.name)) {
            throw IllegalArgumentException("A delivery zone with name '${request.name}' already exists")
        }

        val polygon = parseWkt(request.wktPolygon)
        val zone = DeliveryZone(
            name = request.name,
            baseFee = request.baseFee,
            isActive = request.isActive,
            polygon = polygon
        )

        return DeliveryZoneResponse.from(deliveryZoneRepository.save(zone))
    }

    fun updateDeliveryZone(id: Long, request: UpdateDeliveryZoneRequest): DeliveryZoneResponse {
        val zone = deliveryZoneRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Delivery zone not found with id: $id") }

        if (request.name != null && deliveryZoneRepository.existsByNameAndIdNot(request.name, id)) {
            throw IllegalArgumentException("A delivery zone with name '${request.name}' already exists")
        }

        request.name?.let { zone.name = it }
        request.baseFee?.let { zone.baseFee = it }
        request.isActive?.let { zone.isActive = it }
        request.wktPolygon?.let { zone.polygon = parseWkt(it) }

        return DeliveryZoneResponse.from(deliveryZoneRepository.save(zone))
    }

    fun deleteDeliveryZone(id: Long) {
        if (!deliveryZoneRepository.existsById(id)) {
            throw IllegalArgumentException("Delivery zone not found with id: $id")
        }
        deliveryZoneRepository.deleteById(id)
    }

    private fun parseWkt(wkt: String): org.locationtech.jts.geom.Polygon {
        return try {
            WKTReader(geometryFactory).read(wkt) as org.locationtech.jts.geom.Polygon
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid WKT Polygon: ${e.message}")
        }
    }
}
