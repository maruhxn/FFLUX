package com.maruhxn.fflux.broadcast.application.required

import java.time.LocalDateTime

/** 미디어 서버가 지금 송출을 받고 있는 경로를 읽는 포트. 상태 맞추기에서만 쓴다. */
interface MediaPublishingFinder {
    /** 송출을 받고 있는 경로 전부. 조회에 실패하면 예외를 던진다. */
    fun findPublishings(): List<MediaPublishing>
}

/** 미디어 서버가 [mediaPath]로 송출을 받기 시작한 시각이 [availableAt]이다. */
data class MediaPublishing(val mediaPath: String, val availableAt: LocalDateTime)
