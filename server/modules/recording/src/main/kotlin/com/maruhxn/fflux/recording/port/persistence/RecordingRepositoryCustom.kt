package com.maruhxn.fflux.recording.port.persistence

import com.maruhxn.fflux.recording.domain.Recording

interface RecordingRepositoryCustom {
    fun findReplayPage(cursor: Long?, limit: Int): List<Recording>
}
