package com.maruhxn.fflux.broadcast.port.web

import com.maruhxn.fflux.broadcast.application.provided.BroadcastPage
import com.maruhxn.fflux.broadcast.application.provided.BroadcastResult
import com.maruhxn.fflux.broadcast.application.provided.EditBroadcastCommand
import com.maruhxn.fflux.broadcast.application.provided.FindBroadcast
import com.maruhxn.fflux.broadcast.application.provided.ManageBroadcast
import com.maruhxn.fflux.broadcast.application.provided.RegisterBroadcastCommand
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import com.maruhxn.fflux.common.auth.LoginMember
import com.maruhxn.fflux.common.auth.PublicApi
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
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
) {

    @PostMapping
    fun register(member: LoginMember, @Valid @RequestBody request: BroadcastRequest): ResponseEntity<RegisterBroadcastResponse> {
        val id = manageBroadcast.register(
            RegisterBroadcastCommand(member.memberId, member.nickname, request.title, request.description),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterBroadcastResponse(id))
    }

    @PatchMapping("/{id}")
    fun edit(member: LoginMember, @PathVariable id: Long, @Valid @RequestBody request: BroadcastRequest): ResponseEntity<Unit> {
        manageBroadcast.edit(EditBroadcastCommand(id, member.memberId, request.title, request.description))
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}")
    fun delete(member: LoginMember, @PathVariable id: Long): ResponseEntity<Unit> {
        manageBroadcast.delete(id, member.memberId)
        return ResponseEntity.noContent().build()
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

data class BroadcastRequest(
    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 50, message = "제목은 50자 이하입니다.")
    val title: String,
    @Size(max = 500, message = "설명은 500자 이하입니다.")
    val description: String?,
)

data class RegisterBroadcastResponse(val id: Long)
