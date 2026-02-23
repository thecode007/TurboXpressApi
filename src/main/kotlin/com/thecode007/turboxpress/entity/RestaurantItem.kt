package com.thecode007.turboxpress.entity

import jakarta.persistence.*

@Entity
@Table(name = "restaurant_items")
class RestaurantItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false)
    var price: Double,

    @ElementCollection
    @CollectionTable(name = "restaurant_item_photos", joinColumns = [JoinColumn(name = "item_id")])
    @Column(name = "photo_url", length = 500)
    var photoUrls: MutableList<String> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    var restaurant: Restaurant
)
