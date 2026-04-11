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

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
