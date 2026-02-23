package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import org.locationtech.jts.geom.Point

@Entity
@Table(name = "restaurants")
class Restaurant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @Column(name = "logo_url", length = 500)
    var logoUrl: String? = null,

    @Column(columnDefinition = "POINT", nullable = false)
    var location: Point,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_phone_number", referencedColumnName = "phone_number", nullable = false)
    var owner: Owner,

    @OneToMany(mappedBy = "restaurant", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<RestaurantItem> = mutableListOf()
)
