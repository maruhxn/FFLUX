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
 * 수정·삭제·송출 준비는 행위자가 스트리머인지(403) 먼저 보고, 그다음 상태 조건(409)을 본다.
 * 상태 전이 메서드는 전이했으면 true, 이미 목표 상태라 바꿀 것이 없으면 false를 돌려주고, 할 수 없는 전이면 409를 던진다.
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

    @Column(nullable = false)
    var recordingEnabled: Boolean = false
        protected set

    /** MediaMTX path. 송출을 준비해야 정해진다. 녹화하면 `rec/{id}`, 아니면 `live/{id}`. */
    @Column(length = 30)
    var mediaPath: String? = null
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

    /** 송출 토큰을 받기 전에 스트리머가 READY 방송의 녹화 여부를 정한다. 다시 하면 마지막 선택을 따른다. */
    fun preparePublish(memberId: Long, record: Boolean) {
        checkStreamer(memberId)
        if (status != BroadcastStatus.READY) throw ConflictException("방송 대기 중인 방송만 송출을 시작할 수 있습니다.")
        recordingEnabled = record
        mediaPath = "${if (record) "rec" else "live"}/$id"
    }

    /** 송출을 준비한 경로로, 아직 송출을 시작하지 않은 방송인지. 송출 허가에서 본다. */
    fun canStartPublishAt(path: String): Boolean =
        deletedAt == null && status == BroadcastStatus.READY && mediaPath == path

    /** 시청할 수 있는 방송인지. 시청 허가에서 본다. */
    fun isWatchable(): Boolean = deletedAt == null && status == BroadcastStatus.LIVE

    /** 송출 시작 확인: 미디어 서버가 영상을 받기 시작하면 READY → LIVE. */
    fun confirmPublishStarted(): Boolean = when (status) {
        BroadcastStatus.READY -> true.also { status = BroadcastStatus.LIVE }
        BroadcastStatus.LIVE -> false
        BroadcastStatus.ENDED -> throw ConflictException("끝난 방송은 다시 송출할 수 없습니다.")
    }

    /** 송출 끊김: 미디어 서버에 영상이 더 들어오지 않으면 LIVE → ENDED. */
    fun endByDisconnect(): Boolean = when (status) {
        BroadcastStatus.LIVE -> true.also { status = BroadcastStatus.ENDED }
        BroadcastStatus.ENDED -> false
        BroadcastStatus.READY -> throw ConflictException("송출하지 않은 방송은 끝낼 수 없습니다.")
    }

    /** 종료 요청: 스트리머가 직접 LIVE 방송을 끝낸다. 송출 끊김이 먼저 끝냈으면 그대로 둔다. */
    fun requestEnd(memberId: Long): Boolean {
        checkStreamer(memberId)
        return when (status) {
            BroadcastStatus.LIVE -> true.also { status = BroadcastStatus.ENDED }
            BroadcastStatus.ENDED -> false
            BroadcastStatus.READY -> throw ConflictException("방송 중이 아니라 종료할 수 없습니다. 송출하지 않은 방송은 삭제하세요.")
        }
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
