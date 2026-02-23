package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "owners")
data class Owner(
    @Id
    @Column(name = "phone_number", length = 20)
    val phoneNumber: String,
    
    @Column(name = "full_name", nullable = false)
    var fullName: String,
    
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,
    
    @Column(name = "profile_picture_url", length = 500)
    var profilePictureUrl: String? = null,
    
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
