package com.maruhxn.fflux.recording.application.required

import com.maruhxn.fflux.recording.domain.Recording
import java.time.LocalDateTime

interface RecordingRepository {
    fun save(recording: Recording): Recording

    fun findByBroadcastId(broadcastId: Long): Recording?

    /** 방송의 녹화를 행 잠금(`SELECT … FOR UPDATE`)으로 읽는다. */
    fun findForUpdateByBroadcastId(broadcastId: Long): Recording?

    /** 방송의 녹화를 아직 숨기지 않았으면 숨기고, 바뀐 행 수를 돌려준다. */
    fun hide(broadcastId: Long, now: LocalDateTime): Int

    /** 숨기지 않은 READY 녹화를 방송 ID 내림차순으로 [limit]개까지 읽는다. [cursor]가 있으면 그보다 작은 방송 ID만 읽는다. */
    fun findReplayPage(cursor: Long?, limit: Int): List<Recording>
}
