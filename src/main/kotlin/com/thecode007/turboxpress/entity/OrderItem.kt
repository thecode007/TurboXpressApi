package com.thecode007.turboxpress.entity

import jakarta.persistence.*

@Entity
@Table(name = "order_items")
class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    var menuItem: RestaurantItem,

    @Column(nullable = false)
    var quantity: Int,

    @Column(name = "price_at_order", nullable = false)
    var priceAtOrder: Double
)
