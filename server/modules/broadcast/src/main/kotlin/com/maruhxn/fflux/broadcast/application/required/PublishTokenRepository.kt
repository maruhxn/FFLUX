package com.maruhxn.fflux.broadcast.application.required

import com.maruhxn.fflux.broadcast.domain.PublishToken
import java.time.LocalDateTime

interface PublishTokenRepository {
    fun save(token: PublishToken): PublishToken

    fun findByTokenHash(tokenHash: String): PublishToken?

    /** 방송의 토큰 중 소비된 것이 있는지. 있으면 이미 송출을 시작한 방송이다. */
    fun existsByBroadcastIdAndConsumedAtIsNotNull(broadcastId: Long): Boolean

    /** 미만료·미소비·미무효화 토큰일 때만 소비 시각을 기록한다. 영향 행 수(0 또는 1)를 돌려준다. */
    fun consume(id: Long, now: LocalDateTime): Int

    /** 방송의 미소비 토큰을 모두 무효화한다. */
    fun revokeUnconsumed(broadcastId: Long, now: LocalDateTime): Int
}
