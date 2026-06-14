package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import org.locationtech.jts.geom.Point
import java.math.BigDecimal

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

    @Column(columnDefinition = "geometry(Point, 4326)", nullable = false)
    var location: Point,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", referencedColumnName = "phone_number", nullable = false)
    var owner: Owner,

    @Column(name = "monthly_sub_fee", nullable = false)
    var monthlySubFee: Double = 0.0,

    @Column(name = "commission_rate", nullable = false)
    var commissionRate: Double = 0.0,

    @Column(name = "next_billing_date", nullable = false)
    var nextBillingDate: java.time.LocalDate = java.time.LocalDate.now().plusMonths(1),

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "carried_over_balance", nullable = false)
    var carriedOverBalance: Double = 0.0,

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    var balance: BigDecimal = BigDecimal.ZERO,

    @OneToMany(mappedBy = "restaurant", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<RestaurantItem> = mutableListOf()
)
