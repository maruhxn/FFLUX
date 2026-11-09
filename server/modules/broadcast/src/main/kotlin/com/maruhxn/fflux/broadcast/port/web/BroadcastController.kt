package com.maruhxn.fflux.broadcast.port.web

import com.maruhxn.fflux.broadcast.application.provided.BroadcastDetailResult
import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.broadcast.application.provided.BroadcastModifier
import com.maruhxn.fflux.broadcast.application.provided.BroadcastPage
import com.maruhxn.fflux.broadcast.application.provided.BroadcastStatusChanger
import com.maruhxn.fflux.broadcast.application.provided.EditBroadcastCommand
import com.maruhxn.fflux.broadcast.application.provided.IssuePublishTokenCommand
import com.maruhxn.fflux.broadcast.application.provided.PublishTokenIssuer
import com.maruhxn.fflux.broadcast.application.provided.RegisterBroadcastCommand
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import com.maruhxn.fflux.common.auth.LoginMember
import com.maruhxn.fflux.common.auth.PublicApi
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/broadcasts")
class BroadcastController(
    private val broadcastModifier: BroadcastModifier,
    private val broadcastFinder: BroadcastFinder,
    private val publishTokenIssuer: PublishTokenIssuer,
    private val broadcastStatusChanger: BroadcastStatusChanger,
) {

    @PostMapping
    fun register(member: LoginMember, @RequestBody request: BroadcastRequest): ResponseEntity<RegisterBroadcastResponse> {
        val id = broadcastModifier.register(
            RegisterBroadcastCommand(member.memberId, member.nickname, request.title, request.description),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterBroadcastResponse(id))
    }

    @PatchMapping("/{id}")
    fun edit(member: LoginMember, @PathVariable id: Long, @RequestBody request: BroadcastRequest): ResponseEntity<Unit> {
        broadcastModifier.edit(EditBroadcastCommand(id, member.memberId, request.title, request.description))
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}")
    fun delete(member: LoginMember, @PathVariable id: Long): ResponseEntity<Unit> {
        broadcastModifier.delete(id, member.memberId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/publish-token")
    fun publishToken(member: LoginMember, @PathVariable id: Long, @RequestBody request: PublishTokenRequest): PublishTokenResponse {
        val result = publishTokenIssuer.issue(IssuePublishTokenCommand(id, member.memberId, request.record))
        return PublishTokenResponse(token = result.token, whipUrl = MediaUrls.whip(result.mediaPath))
    }

    @PostMapping("/{id}/end")
    fun end(member: LoginMember, @PathVariable id: Long): ResponseEntity<Unit> {
        broadcastStatusChanger.requestEnd(id, member.memberId)
        return ResponseEntity.noContent().build()
    }

    @PublicApi
    @GetMapping
    fun list(@RequestParam status: BroadcastStatus, @RequestParam(required = false) cursor: Long?): BroadcastPage =
        broadcastFinder.findPublic(status, cursor)

    @GetMapping("/mine")
    fun mine(member: LoginMember, @RequestParam(required = false) cursor: Long?): BroadcastPage =
        broadcastFinder.findMine(member.memberId, cursor)

    @PublicApi
    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): BroadcastDetailResponse = BroadcastDetailResponse.of(broadcastFinder.find(id))
}

/** 제목·설명의 규칙(공백 제거, 길이)은 도메인 값 객체가 검사한다. */
data class BroadcastRequest(
    val title: String,
    val description: String?,
)

data class RegisterBroadcastResponse(val id: Long)

data class PublishTokenRequest(val record: Boolean)

/** [token]은 WHIP 요청의 `Authorization: Bearer`로 보낸다. 로그에 남지 않도록 [toString]에서 가린다. */
data class PublishTokenResponse(val token: String, val whipUrl: String) {
    override fun toString() = "PublishTokenResponse(token=****, whipUrl=$whipUrl)"
}

/** 방송 상세. 시청 주소는 LIVE 방송에만 있다. WHEP가 안 되면 HLS로 재생한다. */
data class BroadcastDetailResponse(
    val id: Long,
    val streamerId: Long,
    val streamerNickname: String,
    val title: String,
    val description: String?,
    val status: BroadcastStatus,
    val createdAt: LocalDateTime,
    /** LIVE가 된 시각(방송 경과 시간의 기준). LIVE가 된 적 없으면 null. */
    val startedAt: LocalDateTime?,
    val whepUrl: String?,
    val hlsUrl: String?,
    /** LIVE 방송의 지금 시청자 수. LIVE가 아니면 null. */
    val viewerCount: Int?,
) {
    companion object {
        fun of(result: BroadcastDetailResult) = with(result.broadcast) {
            BroadcastDetailResponse(
                id = id,
                streamerId = streamerId,
                streamerNickname = streamerNickname,
                title = title,
                description = description,
                status = status,
                createdAt = createdAt,
                startedAt = startedAt,
                whepUrl = result.watchPath?.let(MediaUrls::whep),
                hlsUrl = result.watchPath?.let(MediaUrls::hls),
                viewerCount = viewerCount,
            )
        }
    }
}

/** 미디어 서버(MediaMTX)는 같은 도메인의 `/media` 아래에 있다(Caddy가 넘긴다). */
private object MediaUrls {
    fun whip(mediaPath: String) = "/media/$mediaPath/whip"
    fun whep(mediaPath: String) = "/media/$mediaPath/whep"
    fun hls(mediaPath: String) = "/media/$mediaPath/index.m3u8"
}
