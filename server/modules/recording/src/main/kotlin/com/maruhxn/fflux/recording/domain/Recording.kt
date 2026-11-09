package com.maruhxn.fflux.recording.domain

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
 * 방송 하나의 녹화 파일과 그 처리 상태. 방송 1건에 녹화 1건이고, 미디어 서버의 세그먼트 처리 통지로 만든다.
 * 통지는 반복되거나 순서가 바뀌어 올 수 있다. 상태 메서드는 바꿨으면 true, 이미 그 결과라 바꿀 것이 없으면 false를 돌려주고,
 * 처음 녹화한 세그먼트가 아닌 통지(두 번째 세그먼트)면 409를 던진다.
 */
@Entity
@Table(name = "recording")
class Recording protected constructor(
    broadcastId: Long,
    streamerId: Long,
    segmentPath: String,
    status: RecordingStatus,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val broadcastId: Long = broadcastId

    @Column(nullable = false)
    val streamerId: Long = streamerId

    /** 미디어 서버가 녹화한 원본 세그먼트 경로. 같은 통지의 반복과 두 번째 세그먼트를 가른다. */
    @Column(nullable = false)
    val segmentPath: String = segmentPath

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    var status: RecordingStatus = status
        protected set

    /** 재생할 MP4의 녹화 디렉터리 기준 경로(예: `rec/12/2026-11-10_21-00-00-000000.mp4`). READY가 되어야 생긴다. */
    var filePath: String? = null
        protected set

    var durationSeconds: Long? = null
        protected set

    @Column(nullable = false)
    val createdAt: LocalDateTime = createdAt

    /** 방송이 삭제되어 숨긴 시각. 파일과 행은 남긴다. 한 번만 채우는 값이라 조건부 UPDATE로 쓴다. */
    var hiddenAt: LocalDateTime? = null
        protected set

    /** 처리 시작 통지. 이미 처리했거나 실패한 녹화는 그대로 둔다(늦게 온 통지). */
    fun startProcessing(segmentPath: String): Boolean {
        checkSegment(segmentPath)
        return false
    }

    /** 완료 통지. PROCESSING·FAILED(손으로 다시 처리한 경우)에서 READY가 된다. */
    fun complete(segmentPath: String, filePath: String, durationSeconds: Long?): Boolean {
        checkSegment(segmentPath)
        if (status == RecordingStatus.READY) return false
        status = RecordingStatus.READY
        this.filePath = filePath
        this.durationSeconds = durationSeconds
        return true
    }

    /** 실패 통지. PROCESSING에서만 FAILED가 되고, READY는 되돌리지 않는다. */
    fun fail(segmentPath: String): Boolean {
        checkSegment(segmentPath)
        if (status != RecordingStatus.PROCESSING) return false
        status = RecordingStatus.FAILED
        return true
    }

    /** READY는 모두에게, 처리 중·실패는 스트리머에게만 보인다. 숨긴 녹화는 아무에게도 보이지 않는다. */
    fun isVisibleTo(memberId: Long?): Boolean =
        hiddenAt == null && (status == RecordingStatus.READY || memberId == streamerId)

    private fun checkSegment(segmentPath: String) {
        if (this.segmentPath != segmentPath) throw ConflictException("방송 하나에는 녹화 하나만 둡니다.")
    }

    companion object {
        /** 처리 시작 통지가 처음 온 녹화. */
        fun processing(broadcastId: Long, streamerId: Long, segmentPath: String, now: LocalDateTime) =
            Recording(broadcastId, streamerId, segmentPath, RecordingStatus.PROCESSING, now)

        /** 완료 통지가 처리 시작보다 먼저 온 녹화. */
        fun ready(broadcastId: Long, streamerId: Long, segmentPath: String, filePath: String, durationSeconds: Long?, now: LocalDateTime) =
            Recording(broadcastId, streamerId, segmentPath, RecordingStatus.PROCESSING, now)
                .apply { complete(segmentPath, filePath, durationSeconds) }

        /** 실패 통지가 처리 시작보다 먼저 온 녹화. */
        fun failed(broadcastId: Long, streamerId: Long, segmentPath: String, now: LocalDateTime) =
            Recording(broadcastId, streamerId, segmentPath, RecordingStatus.PROCESSING, now)
                .apply { fail(segmentPath) }
    }
}
