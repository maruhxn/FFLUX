package com.maruhxn.fflux.recording.port.web

import com.maruhxn.fflux.common.auth.PublicApi
import com.maruhxn.fflux.recording.application.provided.RecordingFinder
import com.maruhxn.fflux.recording.application.provided.ReplayPage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/replays")
class ReplayController(
    private val recordingFinder: RecordingFinder,
) {
    /** 다시보기 목록. 재생은 방송 상세(`/api/broadcasts/{id}`)의 `recording.url`로 한다. */
    @PublicApi
    @GetMapping
    fun list(@RequestParam(required = false) cursor: Long?): ReplayPage = recordingFinder.findReplays(cursor)
}
