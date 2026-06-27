package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * A desktop-managed Customer record.
 * Created automatically when an order is placed for a phone number that
 * doesn't already exist. One customer per unique phone number.
 *
 * The zone is the customer's last known delivery area.
 * latitude/longitude (nullable) hold the exact doorstep coordinates
 * recorded by the driver upon delivery. When null, delivery uses the
 * zone centroid instead.
 */
@Entity
@Table(name = "customers")
class Customer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** FK to the auth user row. Nullable — customer may not have a login. */
    @Column(name = "user_id", columnDefinition = "VARCHAR(36)")
    var userId: UUID? = null,

    @Column(name = "full_name", nullable = false, length = 255)
    var fullName: String,

    @Column(name = "phone_number", nullable = false, unique = true, length = 50)
    var phoneNumber: String,

    /** Customer's assigned delivery zone (changes over time). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_zone_id")
    var deliveryZone: DeliveryZone? = null,

    /**
     * Exact pinned coordinates — set by the driver on first DELIVERED event,
     * and reset to null by the operator if the customer moves.
     */
    @Column(name = "latitude")
    var latitude: Double? = null,

    @Column(name = "longitude")
    var longitude: Double? = null,

    @Column(name = "detailed_address", length = 500)
    var detailedAddress: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
