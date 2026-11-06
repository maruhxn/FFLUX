package com.maruhxn.fflux.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "member")
class Member protected constructor(
    username: Username,
    encodedPassword: String,
    nickname: Nickname,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Embedded
    val username: Username = username

    @Column(name = "password", nullable = false, length = 100)
    val encodedPassword: String = encodedPassword

    @Embedded
    val nickname: Nickname = nickname

    @Column(nullable = false)
    val createdAt: LocalDateTime = createdAt

    companion object {
        /** 회원으로 가입한다. 비밀번호는 암호화한 값만 받는다. */
        fun register(username: Username, encodedPassword: String, nickname: Nickname, now: LocalDateTime) =
            Member(username, encodedPassword, nickname, now)
    }
}
