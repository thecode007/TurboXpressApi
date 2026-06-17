package com.thecode007.turboxpress.entity

import jakarta.persistence.*

@Entity
@Table(name = "restaurant_categories")
class RestaurantCategory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    var restaurant: Restaurant
)
