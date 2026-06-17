package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    var restaurant: Restaurant,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_phone_number", referencedColumnName = "phone_number")
    var driver: DeliveryGuy? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "total_amount", nullable = false)
    var totalAmount: Double = 0.0,

    @Column(name = "platform_commission_amount", nullable = false)
    var platformCommissionAmount: Double = 0.0,

    @Column(name = "is_settled", nullable = false)
    var isSettled: Boolean = false,

    @Column(name = "delivery_fee", nullable = false)
    var deliveryFee: Double = 0.0,

    @Column(name = "is_settled_driver", nullable = false)
    var isSettledDriver: Boolean = false,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<OrderItem> = mutableListOf(),

    @Column(name = "customer_name", nullable = false)
    var customerName: String = "",

    @Column(name = "customer_phone", nullable = false)
    var customerPhone: String = "",

    @Column(name = "location_method", nullable = false)
    var locationMethod: String = "",

    @Column(name = "delivery_zone_id")
    var deliveryZoneId: Long? = null,

    @Column(name = "whatsapp_map_link", length = 500)
    var whatsappMapLink: String? = null,

    @Column(name = "detailed_address", length = 500)
    var detailedAddress: String? = null,

    @Column(name = "latitude")
    var latitude: Double? = null,

    @Column(name = "longitude")
    var longitude: Double? = null,

    @Column(name = "route_distance_km")
    var routeDistanceKm: Double? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
