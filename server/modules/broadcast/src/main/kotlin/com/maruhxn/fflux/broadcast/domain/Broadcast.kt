package com.maruhxn.fflux.broadcast.domain

import com.maruhxn.fflux.common.error.ConflictException
import jakarta.persistence.Column
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
 * 상태 조건(수정은 READY·LIVE, 삭제는 READY·ENDED)은 여기서 판단하고, 스트리머 확인은 application 서비스가 한다.
 */
@Entity
@Table(name = "broadcast")
class Broadcast(
    streamerId: Long,
    streamerNickname: String,
    title: String,
    description: String?,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val streamerId: Long = streamerId

    @Column(nullable = false, length = 12)
    val streamerNickname: String = streamerNickname

    @Column(nullable = false, length = 50)
    var title: String = title
        protected set

    @Column(length = 500)
    var description: String? = description
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

    fun isStreamer(memberId: Long): Boolean = streamerId == memberId

    fun edit(title: String, description: String?) {
        if (status == BroadcastStatus.ENDED) throw ConflictException("끝난 방송은 수정할 수 없습니다.")
        this.title = title
        this.description = description
    }

    fun delete(now: LocalDateTime) {
        if (status == BroadcastStatus.LIVE) throw ConflictException("방송 중에는 삭제할 수 없습니다. 방송을 먼저 종료하세요.")
        deletedAt = now
    }
}
