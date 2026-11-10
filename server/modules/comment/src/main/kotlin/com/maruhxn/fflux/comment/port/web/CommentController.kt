package com.maruhxn.fflux.comment.port.web

import com.maruhxn.fflux.comment.application.provided.CommentFinder
import com.maruhxn.fflux.comment.application.provided.CommentModifier
import com.maruhxn.fflux.comment.application.provided.CommentPage
import com.maruhxn.fflux.comment.application.provided.EditCommentCommand
import com.maruhxn.fflux.comment.application.provided.ReplyCommand
import com.maruhxn.fflux.comment.application.provided.ReplyPage
import com.maruhxn.fflux.comment.application.provided.WriteCommentCommand
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

/**
 * 댓글은 방송 아래에서 쓰고 읽고(`/api/broadcasts/{id}/comments`), 댓글 ID로 고치고 지운다(`/api/comments/{id}`).
 * 답글은 원댓글 아래에서 쓰고 읽고(`/api/comments/{id}/replies`), 고치고 지우는 것은 댓글과 같다.
 */
@RestController
@RequestMapping("/api")
class CommentController(
    private val commentModifier: CommentModifier,
    private val commentFinder: CommentFinder,
) {
    @PublicApi
    @GetMapping("/broadcasts/{broadcastId}/comments")
    fun list(@PathVariable broadcastId: Long, @RequestParam(required = false) cursor: Long?): CommentPage =
        commentFinder.findPage(broadcastId, cursor)

    @PostMapping("/broadcasts/{broadcastId}/comments")
    fun write(member: LoginMember, @PathVariable broadcastId: Long, @RequestBody request: CommentRequest): ResponseEntity<WriteCommentResponse> {
        val id = commentModifier.write(WriteCommentCommand(broadcastId, member.memberId, member.nickname, request.content))
        return ResponseEntity.status(HttpStatus.CREATED).body(WriteCommentResponse(id))
    }

    @PublicApi
    @GetMapping("/comments/{id}/replies")
    fun replies(@PathVariable id: Long, @RequestParam(required = false) cursor: Long?): ReplyPage =
        commentFinder.findReplies(id, cursor)

    @PostMapping("/comments/{id}/replies")
    fun reply(member: LoginMember, @PathVariable id: Long, @RequestBody request: CommentRequest): ResponseEntity<WriteCommentResponse> {
        val replyId = commentModifier.reply(ReplyCommand(id, member.memberId, member.nickname, request.content))
        return ResponseEntity.status(HttpStatus.CREATED).body(WriteCommentResponse(replyId))
    }

    @PatchMapping("/comments/{id}")
    fun edit(member: LoginMember, @PathVariable id: Long, @RequestBody request: CommentRequest): ResponseEntity<Unit> {
        commentModifier.edit(EditCommentCommand(id, member.memberId, request.content))
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/comments/{id}")
    fun delete(member: LoginMember, @PathVariable id: Long): ResponseEntity<Unit> {
        commentModifier.delete(id, member.memberId)
        return ResponseEntity.noContent().build()
    }
}

/** 본문 규칙(공백 제거, 길이)은 도메인 값 객체가 검사한다. */
data class CommentRequest(val content: String)

data class WriteCommentResponse(val id: Long)
