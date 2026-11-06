package com.maruhxn.fflux.broadcast.port.persistence

import com.maruhxn.fflux.broadcast.application.required.BroadcastRepository
import com.maruhxn.fflux.broadcast.domain.Broadcast
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface BroadcastJpaRepository : JpaRepository<Broadcast, Long>, BroadcastRepository, BroadcastRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Broadcast b WHERE b.id = :id AND b.deletedAt IS NULL")
    override fun findForUpdate(id: Long): Broadcast?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Broadcast b WHERE b.mediaPath = :mediaPath AND b.deletedAt IS NULL")
    override fun findForUpdateByMediaPath(mediaPath: String): Broadcast?
}
