package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "full_name", nullable = false)
    var fullName: String,

    var username: String,

    @Column(name = "phone_number", unique = true, nullable = false, length = 20)
    var phoneNumber: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    var roles: MutableSet<Role> = mutableSetOf()
)
