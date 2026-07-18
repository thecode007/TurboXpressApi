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
    @JoinColumn(name = "driver_id", referencedColumnName = "user_id")
    var driver: DriverProfile? = null,

    /** FK to the customers table — carries all customer location info. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    var customer: Customer,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.PREPARING,

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    var deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,

    @Column(name = "driver_arrived_at_restaurant_at")
    var driverArrivedAtRestaurantAt: Instant? = null,

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

    @Column(name = "route_distance_km")
    var routeDistanceKm: Double? = null,

    @Column(name = "accepted_at")
    var acceptedAt: Instant? = null,

    @Column(name = "ready_at")
    var readyAt: Instant? = null,

    @Column(name = "picked_up_at")
    var pickedUpAt: Instant? = null,

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
