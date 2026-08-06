package com.thecode007.turboxpress.dto

data class OrderCreateRequest(
    /** Discriminator — one of: FOOD_DELIVERY, ROOM_SERVICE, TAXI */
    val orderType: String = "FOOD_DELIVERY",

    // ── FOOD_DELIVERY only ────────────────────────────────────────────────────
    /** Required for FOOD_DELIVERY. Ignored (can be 0) for ROOM_SERVICE / TAXI. */
    val restaurantId: Long = 0,
    val items: List<OrderItemRequest> = emptyList(),

    // ── Customer ──────────────────────────────────────────────────────────────
    /** Customer phone number — used to look up or create the customer record. */
    val customerPhone: String,
    val customerName: String? = null,

    // ── Location (FOOD_DELIVERY) ──────────────────────────────────────────────
    /** Optional: override the customer's zone for this order (also saves back to customer). */
    val deliveryZoneId: Long? = null,
    val detailedAddress: String? = null,
    val routeDistanceKm: Double? = null,

    // ── Cost ──────────────────────────────────────────────────────────────────
    val deliveryFee: Double = 0.0,
    val customDescription: String? = null,
    val customItemsCost: Double? = null,

    // ── ROOM_SERVICE / TAXI ───────────────────────────────────────────────────
    /** Free-text pickup location (supermarket name, address, etc.). */
    val sourceName: String? = null,
    /** Free-text drop-off location (customer address, room number, etc.). */
    val destinationName: String? = null,
    /**
     * Pre-assigned driver phone number.
     * When provided for ROOM_SERVICE orders, the driver is automatically assigned
     * right after the order is created (no broadcast needed).
     */
    val driverPhoneNumber: String? = null
)
