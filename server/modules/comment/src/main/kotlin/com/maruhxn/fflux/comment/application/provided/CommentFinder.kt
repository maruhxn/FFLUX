package com.maruhxn.fflux.comment.application.provided

import java.time.LocalDateTime

interface CommentFinder {
    /** 방송의 댓글을 최신순으로 읽는다. 삭제된 댓글은 자리만 남긴다. [cursor]는 앞 페이지의 `nextCursor`다. */
    fun findPage(broadcastId: Long, cursor: Long?): CommentPage

    /** 원댓글의 답글을 오래된순으로 읽는다. 삭제된 답글은 자리만 남긴다. [cursor]는 앞 페이지의 `nextCursor`다. */
    fun findReplies(parentId: Long, cursor: Long?): ReplyPage
}

/**
 * 삭제된 댓글([deleted])은 작성자와 본문을 가려 [authorId], [authorNickname], [content]가 null이다.
 * [replyCount]는 원댓글의 삭제되지 않은 답글 수이고, 답글에서는 0이다.
 */
data class CommentResult(
    val id: Long,
    val authorId: Long?,
    val authorNickname: String?,
    val content: String?,
    val createdAt: LocalDateTime,
    val edited: Boolean,
    val deleted: Boolean,
    val replyCount: Long,
)

/** [count]는 삭제된 댓글을 뺀 방송의 댓글 수(답글 포함)다. [nextCursor]가 null이면 마지막 페이지다. */
data class CommentPage(
    val items: List<CommentResult>,
    val nextCursor: Long?,
    val count: Long,
)

/** [nextCursor]가 null이면 마지막 페이지다. */
data class ReplyPage(
    val items: List<CommentResult>,
    val nextCursor: Long?,
)
