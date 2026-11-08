package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize

@Entity
@Table(
    name = "order_items",
    indexes = [
        Index(name = "idx_order_items_order_id", columnList = "order_id"),
        Index(name = "idx_order_items_menu_item_id", columnList = "menu_item_id")
    ]
)
class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order,

    @BatchSize(size = 100)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    var menuItem: RestaurantItem,

    @Column(nullable = false)
    var quantity: Int,

    @Column(name = "price_at_order", nullable = false)
    var priceAtOrder: Double
)
