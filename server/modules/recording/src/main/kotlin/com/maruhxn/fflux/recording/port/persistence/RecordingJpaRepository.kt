package com.maruhxn.fflux.recording.port.persistence

import com.maruhxn.fflux.recording.application.required.RecordingRepository
import com.maruhxn.fflux.recording.domain.Recording
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface RecordingJpaRepository : JpaRepository<Recording, Long>, RecordingRepository, RecordingRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Recording r WHERE r.broadcastId = :broadcastId")
    override fun findForUpdateByBroadcastId(broadcastId: Long): Recording?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Recording r SET r.hiddenAt = :now WHERE r.broadcastId = :broadcastId AND r.hiddenAt IS NULL")
    override fun hide(broadcastId: Long, now: LocalDateTime): Int
}
