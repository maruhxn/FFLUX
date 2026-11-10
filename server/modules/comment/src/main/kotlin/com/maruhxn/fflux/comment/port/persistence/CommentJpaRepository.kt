package com.maruhxn.fflux.comment.port.persistence

import com.maruhxn.fflux.comment.application.required.CommentRepository
import com.maruhxn.fflux.comment.domain.Comment
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface CommentJpaRepository : JpaRepository<Comment, Long>, CommentRepository, CommentRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comment c WHERE c.id = :id AND c.hiddenAt IS NULL")
    override fun findForUpdateById(id: Long): Comment?

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.broadcastId = :broadcastId AND c.deletedAt IS NULL AND c.hiddenAt IS NULL")
    override fun countVisible(broadcastId: Long): Long

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.hiddenAt = :now WHERE c.broadcastId = :broadcastId AND c.hiddenAt IS NULL")
    override fun hideAll(broadcastId: Long, now: LocalDateTime): Int
}
