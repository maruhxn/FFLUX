package com.maruhxn.fflux.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "member")
class Member(
    username: String,
    encodedPassword: String,
    nickname: String,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, unique = true, length = 20)
    val username: String = username

    @Column(name = "password", nullable = false, length = 100)
    val encodedPassword: String = encodedPassword

    @Column(nullable = false, length = 12)
    val nickname: String = nickname

    @Column(nullable = false)
    val createdAt: LocalDateTime = createdAt
}
