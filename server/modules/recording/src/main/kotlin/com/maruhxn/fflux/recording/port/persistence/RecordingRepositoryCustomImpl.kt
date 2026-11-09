package com.maruhxn.fflux.recording.port.persistence

import com.maruhxn.fflux.recording.domain.QRecording.Companion.recording
import com.maruhxn.fflux.recording.domain.Recording
import com.maruhxn.fflux.recording.domain.RecordingStatus
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager

class RecordingRepositoryCustomImpl(entityManager: EntityManager) : RecordingRepositoryCustom {

    private val queryFactory = JPAQueryFactory(entityManager)

    override fun findReplayPage(cursor: Long?, limit: Int): List<Recording> =
        queryFactory.selectFrom(recording)
            .where(
                recording.status.eq(RecordingStatus.READY),
                recording.hiddenAt.isNull,
                cursor?.let { recording.broadcastId.lt(it) },
            )
            .orderBy(recording.broadcastId.desc())
            .limit(limit.toLong())
            .fetch()
}
