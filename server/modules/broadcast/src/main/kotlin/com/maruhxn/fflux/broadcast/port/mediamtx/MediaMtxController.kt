package com.maruhxn.fflux.broadcast.port.mediamtx

import com.maruhxn.fflux.broadcast.application.provided.BroadcastStatusChanger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * MediaMTX가 부르는 인증 콜백과 훅. `/internal`은 Caddy가 라우팅하지 않아 Docker 내부망에서만 닿는다.
 * 판단은 모두 [BroadcastStatusChanger]가 하고, 여기서는 HTTP 응답으로 바꾸기만 한다.
 */
@RestController
@RequestMapping("/internal/mediamtx")
class MediaMtxController(
    private val broadcastStatusChanger: BroadcastStatusChanger,
) {

    /** 2xx면 MediaMTX가 허용하고, 그 밖이면 거절한다. `api` 등은 MediaMTX 설정에서 콜백 대상에서 뺀다. */
    @PostMapping("/auth")
    fun auth(@RequestBody request: MediaMtxAuthRequest): ResponseEntity<Unit> {
        val allowed = when (request.action) {
            "publish" -> broadcastStatusChanger.authorizePublish(request.path, request.token)
            "read" -> broadcastStatusChanger.authorizeRead(request.path)
            else -> false
        }
        return ResponseEntity.status(if (allowed) HttpStatus.OK else HttpStatus.UNAUTHORIZED).build()
    }

    /** `runOnAvailable` 훅. 전이할 수 없어도 200으로 응답한다. */
    @PostMapping("/available")
    fun available(@RequestBody request: MediaMtxHookRequest): ResponseEntity<Unit> {
        broadcastStatusChanger.confirmPublishStarted(request.path)
        return ResponseEntity.ok().build()
    }
}

/** MediaMTX 인증 요청 본문 중 쓰는 칸만 받는다. WHIP의 `Authorization: Bearer` 값은 [token]으로 온다. */
data class MediaMtxAuthRequest(
    val action: String,
    val path: String,
    val token: String? = null,
) {
    override fun toString() = "MediaMtxAuthRequest(action=$action, path=$path, token=****)"
}

/** 훅 스크립트(`infra/hooks/notify-server.sh`)가 보내는 본문. */
data class MediaMtxHookRequest(val path: String)
