package com.thecode007.turboxpress.entity

import jakarta.persistence.*

@Entity
@Table(name = "roles")
data class Role(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Column(name = "role_name", unique = true, nullable = false, length = 50)
    var roleName: String,

    @ManyToMany(mappedBy = "roles")
    var users: MutableSet<User> = mutableSetOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Role) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "Role(id=$id, roleName='$roleName')"
}
