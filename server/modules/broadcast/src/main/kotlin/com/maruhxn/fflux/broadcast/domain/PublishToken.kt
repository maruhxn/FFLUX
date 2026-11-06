package com.maruhxn.fflux.broadcast.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64

/**
 * 방송 하나의 송출을 한 번만 허락하는 짧은 수명의 일회용 증표. DB에는 원본이 아니라 SHA-256 해시만 둔다.
 * 소비(1회)와 무효화는 조건부 UPDATE로 하므로 이 엔티티에는 상태를 바꾸는 메서드가 없다.
 */
@Entity
@Table(name = "publish_token")
class PublishToken protected constructor(
    broadcastId: Long,
    tokenHash: String,
    expiresAt: LocalDateTime,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val broadcastId: Long = broadcastId

    @Column(nullable = false, length = 64)
    val tokenHash: String = tokenHash

    @Column(nullable = false)
    val expiresAt: LocalDateTime = expiresAt

    var consumedAt: LocalDateTime? = null
        protected set

    var revokedAt: LocalDateTime? = null
        protected set

    @Column(nullable = false)
    val createdAt: LocalDateTime = createdAt

    companion object {
        val TTL: Duration = Duration.ofMinutes(1)

        private val random = SecureRandom()

        /** 새 토큰을 만든다. 원본은 [IssuedPublishToken.raw]로 한 번만 돌려주고 저장하지 않는다. */
        fun issue(broadcastId: Long, now: LocalDateTime): IssuedPublishToken {
            val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
            return IssuedPublishToken(PublishToken(broadcastId, hash(raw), now.plus(TTL), now), raw)
        }

        fun hash(raw: String): String =
            MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** 발급한 토큰과 그 원본. 원본은 로그에 남지 않도록 [toString]에서 가린다. */
class IssuedPublishToken(val token: PublishToken, val raw: String) {
    override fun toString() = "IssuedPublishToken(****)"
}
