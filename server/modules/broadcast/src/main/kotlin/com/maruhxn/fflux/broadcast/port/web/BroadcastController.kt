package com.maruhxn.fflux.broadcast.port.web

import com.maruhxn.fflux.broadcast.application.provided.BroadcastPage
import com.maruhxn.fflux.broadcast.application.provided.BroadcastResult
import com.maruhxn.fflux.broadcast.application.provided.EditBroadcastCommand
import com.maruhxn.fflux.broadcast.application.provided.FindBroadcast
import com.maruhxn.fflux.broadcast.application.provided.IssuePublishToken
import com.maruhxn.fflux.broadcast.application.provided.IssuePublishTokenCommand
import com.maruhxn.fflux.broadcast.application.provided.ManageBroadcast
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

@RestController
@RequestMapping("/api/broadcasts")
class BroadcastController(
    private val manageBroadcast: ManageBroadcast,
    private val findBroadcast: FindBroadcast,
    private val issuePublishToken: IssuePublishToken,
) {

    @PostMapping
    fun register(member: LoginMember, @RequestBody request: BroadcastRequest): ResponseEntity<RegisterBroadcastResponse> {
        val id = manageBroadcast.register(
            RegisterBroadcastCommand(member.memberId, member.nickname, request.title, request.description),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterBroadcastResponse(id))
    }

    @PatchMapping("/{id}")
    fun edit(member: LoginMember, @PathVariable id: Long, @RequestBody request: BroadcastRequest): ResponseEntity<Unit> {
        manageBroadcast.edit(EditBroadcastCommand(id, member.memberId, request.title, request.description))
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}")
    fun delete(member: LoginMember, @PathVariable id: Long): ResponseEntity<Unit> {
        manageBroadcast.delete(id, member.memberId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/publish-token")
    fun publishToken(member: LoginMember, @PathVariable id: Long, @RequestBody request: PublishTokenRequest): PublishTokenResponse {
        val result = issuePublishToken.issue(IssuePublishTokenCommand(id, member.memberId, request.record))
        // 미디어 서버는 같은 도메인의 /media 아래에 있다(Caddy가 넘긴다).
        return PublishTokenResponse(token = result.token, whipUrl = "/media/${result.mediaPath}/whip")
    }

    @PublicApi
    @GetMapping
    fun list(@RequestParam status: BroadcastStatus, @RequestParam(required = false) cursor: Long?): BroadcastPage =
        findBroadcast.findPublic(status, cursor)

    @GetMapping("/mine")
    fun mine(member: LoginMember, @RequestParam(required = false) cursor: Long?): BroadcastPage =
        findBroadcast.findMine(member.memberId, cursor)

    @PublicApi
    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): BroadcastResult = findBroadcast.find(id)
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
