package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import org.locationtech.jts.geom.Polygon

@Entity
@Table(name = "delivery_zones")
class DeliveryZone(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    var name: String,

    @Column(nullable = false)
    var baseFee: Double,

    @Column(nullable = false)
    var isActive: Boolean = true,

    @Column(columnDefinition = "POLYGON", nullable = false)
    var polygon: Polygon
)
