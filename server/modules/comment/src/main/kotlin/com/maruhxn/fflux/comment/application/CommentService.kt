package com.maruhxn.fflux.comment.application

import com.maruhxn.fflux.comment.application.provided.CommentFinder
import com.maruhxn.fflux.comment.application.provided.CommentModifier
import com.maruhxn.fflux.comment.application.provided.CommentPage
import com.maruhxn.fflux.comment.application.provided.CommentResult
import com.maruhxn.fflux.comment.application.provided.EditCommentCommand
import com.maruhxn.fflux.comment.application.provided.ReplyCommand
import com.maruhxn.fflux.comment.application.provided.ReplyPage
import com.maruhxn.fflux.comment.application.provided.WriteCommentCommand
import com.maruhxn.fflux.comment.application.required.CommentRepository
import com.maruhxn.fflux.comment.application.required.ReplayFinder
import com.maruhxn.fflux.comment.domain.Comment
import com.maruhxn.fflux.comment.domain.CommentContent
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional
class CommentService(
    private val commentRepository: CommentRepository,
    private val replayFinder: ReplayFinder,
    private val clock: Clock,
) : CommentModifier, CommentFinder {

    override fun write(command: WriteCommentCommand): Long {
        val content = CommentContent.of(command.content)
        if (!replayFinder.isReplay(command.broadcastId)) throw ConflictException("다시보기에만 댓글을 쓸 수 있습니다.")
        val comment = Comment.write(command.broadcastId, command.authorId, command.authorNickname, content, LocalDateTime.now(clock))
        return commentRepository.save(comment).id
    }

    /** 원댓글이 있으면 그 방송은 다시보기다(방송이 삭제되면 원댓글이 숨는다). 원댓글을 잠가 동시에 지우는 요청과 순서를 정한다. */
    override fun reply(command: ReplyCommand): Long {
        val content = CommentContent.of(command.content)
        val parent = findForUpdateOrThrow(command.parentId)
        val reply = Comment.reply(parent, command.authorId, command.authorNickname, content, LocalDateTime.now(clock))
        return commentRepository.save(reply).id
    }

    override fun edit(command: EditCommentCommand) {
        val content = CommentContent.of(command.content)
        findForUpdateOrThrow(command.commentId).edit(command.memberId, content, LocalDateTime.now(clock))
    }

    override fun delete(commentId: Long, memberId: Long) {
        findForUpdateOrThrow(commentId).delete(memberId, LocalDateTime.now(clock))
    }

    override fun hideAll(broadcastId: Long) {
        commentRepository.hideAll(broadcastId, LocalDateTime.now(clock))
    }

    /** 한 개를 더 읽어 다음 페이지가 있는지 판단한다. */
    @Transactional(readOnly = true)
    override fun findPage(broadcastId: Long, cursor: Long?): CommentPage {
        val rows = commentRepository.findPage(broadcastId, cursor, PAGE_SIZE + 1)
        val comments = rows.take(PAGE_SIZE)
        val replyCounts = commentRepository.countVisibleReplies(comments.map { it.id })
        return CommentPage(
            items = comments.map { it.toResult(replyCounts[it.id] ?: 0) },
            nextCursor = if (rows.size > PAGE_SIZE) comments.last().id else null,
            count = commentRepository.countVisible(broadcastId),
        )
    }

    @Transactional(readOnly = true)
    override fun findReplies(parentId: Long, cursor: Long?): ReplyPage {
        val rows = commentRepository.findReplies(parentId, cursor, PAGE_SIZE + 1)
        val replies = rows.take(PAGE_SIZE)
        return ReplyPage(
            items = replies.map { it.toResult(replyCount = 0) },
            nextCursor = if (rows.size > PAGE_SIZE) replies.last().id else null,
        )
    }

    private fun findForUpdateOrThrow(commentId: Long): Comment =
        commentRepository.findForUpdateById(commentId) ?: throw NotFoundException("댓글을 찾을 수 없습니다.")

    private fun Comment.toResult(replyCount: Long) =
        if (isDeleted()) {
            CommentResult(id, null, null, null, createdAt, edited = false, deleted = true, replyCount)
        } else {
            CommentResult(id, authorId, authorNickname, content.value, createdAt, isEdited(), deleted = false, replyCount)
        }

    companion object {
        const val PAGE_SIZE = 20
    }
}
