package com.maruhxn.fflux.broadcast.port.web

import com.maruhxn.fflux.broadcast.application.provided.StreamerFinder
import com.maruhxn.fflux.broadcast.application.provided.StreamerResult
import com.maruhxn.fflux.common.auth.PublicApi
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/streamers")
class StreamerController(
    private val streamerFinder: StreamerFinder,
) {
    /** 스트리머 검색. 스트리머의 방송 목록은 `GET /api/broadcasts?status=...&streamerId=`로 읽는다. */
    @PublicApi
    @GetMapping
    fun search(@RequestParam(required = false) q: String?): List<StreamerResult> = streamerFinder.search(q)

    @PublicApi
    @GetMapping("/{streamerId}")
    fun find(@PathVariable streamerId: Long): StreamerResult = streamerFinder.find(streamerId)
}
