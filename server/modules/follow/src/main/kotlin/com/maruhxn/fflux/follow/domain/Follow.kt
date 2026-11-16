package com.maruhxn.fflux.follow.domain

import com.maruhxn.fflux.common.error.ConflictException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 팔로우: 회원([followerId])이 스트리머([streamerId])의 방송 시작 알림을 받겠다고 한 것. 한 쌍에 하나다. */
@Entity
@Table(name = "follow")
class Follow protected constructor(
    followerId: Long,
    streamerId: Long,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val followerId: Long = followerId

    @Column(nullable = false)
    val streamerId: Long = streamerId

    @Column(nullable = false)
    val createdAt: LocalDateTime = createdAt

    companion object {
        /** 회원이 스트리머를 팔로우한다. 자기 자신은 팔로우할 수 없다(409). */
        fun follow(followerId: Long, streamerId: Long, now: LocalDateTime): Follow {
            if (followerId == streamerId) throw ConflictException("자기 자신은 팔로우할 수 없습니다.")
            return Follow(followerId, streamerId, now)
        }
    }
}
