package com.maruhxn.fflux.broadcast.domain

import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 스트리머가 등록한 1회성 라이브 세션. 스트리머 닉네임은 등록 시점 스냅샷이다.
 * 수정·삭제는 행위자가 스트리머인지(403) 먼저 보고, 그다음 상태 조건(409)을 본다.
 */
@Entity
@Table(name = "broadcast")
class Broadcast protected constructor(
    streamerId: Long,
    streamerNickname: String,
    title: BroadcastTitle,
    description: BroadcastDescription?,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val streamerId: Long = streamerId

    @Column(nullable = false, length = 12)
    val streamerNickname: String = streamerNickname

    @Embedded
    var title: BroadcastTitle = title
        protected set

    @Embedded
    var description: BroadcastDescription? = description
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    var status: BroadcastStatus = BroadcastStatus.READY
        protected set

    @Column(nullable = false)
    val createdAt: LocalDateTime = createdAt

    var deletedAt: LocalDateTime? = null
        protected set

    /** 수정은 스트리머만, READY·LIVE에서만 한다. */
    fun edit(memberId: Long, title: BroadcastTitle, description: BroadcastDescription?) {
        checkStreamer(memberId)
        if (status == BroadcastStatus.ENDED) throw ConflictException("끝난 방송은 수정할 수 없습니다.")
        this.title = title
        this.description = description
    }

    /** 삭제(soft delete)는 스트리머만, READY·ENDED에서만 한다. */
    fun delete(memberId: Long, now: LocalDateTime) {
        checkStreamer(memberId)
        if (status == BroadcastStatus.LIVE) throw ConflictException("방송 중에는 삭제할 수 없습니다. 방송을 먼저 종료하세요.")
        deletedAt = now
    }

    private fun checkStreamer(memberId: Long) {
        if (streamerId != memberId) throw ForbiddenException("방송의 스트리머만 할 수 있습니다.")
    }

    companion object {
        /** 회원이 스트리머가 되어 READY 방송을 등록한다. */
        fun register(
            streamerId: Long,
            streamerNickname: String,
            title: BroadcastTitle,
            description: BroadcastDescription?,
            now: LocalDateTime,
        ) = Broadcast(streamerId, streamerNickname, title, description, now)
    }
}
