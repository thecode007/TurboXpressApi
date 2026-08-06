package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * The type of this order.
     * - FOOD_DELIVERY: standard restaurant order (restaurant required)
     * - ROOM_SERVICE:  pick up from any source, deliver to customer (restaurant optional)
     * - TAXI:          transport a passenger (restaurant optional)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    var orderType: OrderType = OrderType.FOOD_DELIVERY,

    /** Free-text pickup location — used by ROOM_SERVICE and TAXI orders. */
    @Column(name = "source_name")
    var sourceName: String? = null,

    /** Free-text drop-off location — used by ROOM_SERVICE and TAXI orders. */
    @Column(name = "destination_name")
    var destinationName: String? = null,

    /**
     * Restaurant — nullable so that ROOM_SERVICE / TAXI orders can be created
     * without a restaurant record. FOOD_DELIVERY orders always have a restaurant.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = true)
    var restaurant: Restaurant? = null,

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

    @Column(name = "custom_description")
    var customDescription: String? = null,

    @Column(name = "custom_items_cost")
    var customItemsCost: Double? = null,

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
