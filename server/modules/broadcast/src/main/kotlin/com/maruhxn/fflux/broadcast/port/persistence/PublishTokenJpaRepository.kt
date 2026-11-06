package com.maruhxn.fflux.broadcast.port.persistence

import com.maruhxn.fflux.broadcast.application.required.PublishTokenRepository
import com.maruhxn.fflux.broadcast.domain.PublishToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface PublishTokenJpaRepository : JpaRepository<PublishToken, Long>, PublishTokenRepository {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE PublishToken t SET t.consumedAt = :now
        WHERE t.id = :id AND t.consumedAt IS NULL AND t.revokedAt IS NULL AND t.expiresAt > :now
        """,
    )
    override fun consume(id: Long, now: LocalDateTime): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE PublishToken t SET t.revokedAt = :now
        WHERE t.broadcastId = :broadcastId AND t.consumedAt IS NULL AND t.revokedAt IS NULL
        """,
    )
    override fun revokeUnconsumed(broadcastId: Long, now: LocalDateTime): Int
}
