package com.thecode007.turboxpress.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.*

/**
 * Core Identity entity - represents a single human user identified by phone number.
 * A user can hold multiple profiles (Customer, Driver, Owner, Admin) simultaneously.
 * Firebase UID is the primary external identity token; passwordHash is kept nullable
 * for backward compatibility with the legacy admin-panel login flow.
 */
@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "full_name", nullable = false)
    var fullName: String,

    @Column(unique = true)
    var username: String? = null,

    @Column(name = "phone_number", unique = true, nullable = false, length = 20)
    var phoneNumber: String,

    /** Used by legacy admin-panel password login. Null for Firebase-only users. */
    @Column(name = "password_hash")
    var passwordHash: String? = null,

    /**
     * Firebase UID from Firebase Auth. Set when the user first logs in via
     * Firebase phone OTP. Unique across all users.
     */
    @Column(name = "firebase_uid", unique = true, length = 128)
    var firebaseUid: String? = null,

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
