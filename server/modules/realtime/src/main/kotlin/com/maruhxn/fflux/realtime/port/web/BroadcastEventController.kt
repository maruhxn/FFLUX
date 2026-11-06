package com.maruhxn.fflux.realtime.port.web

import com.maruhxn.fflux.common.auth.PublicApi
import com.maruhxn.fflux.realtime.application.provided.BroadcastEventSubscriber
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/broadcasts")
class BroadcastEventController(
    private val broadcastEventSubscriber: BroadcastEventSubscriber,
) {
    /** 방송별 SSE. 이벤트: `broadcast-status`(`{status}`), `broadcast-updated`(`{title, description}`). */
    @PublicApi
    @GetMapping("/{id}/events")
    fun subscribe(@PathVariable id: Long): SseEmitter = broadcastEventSubscriber.subscribe(id)
}
