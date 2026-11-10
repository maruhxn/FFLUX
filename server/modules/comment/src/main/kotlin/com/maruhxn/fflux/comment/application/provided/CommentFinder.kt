package com.maruhxn.fflux.comment.application.provided

import java.time.LocalDateTime

interface CommentFinder {
    /** 방송의 댓글을 최신순으로 읽는다. 삭제된 댓글은 자리만 남긴다. [cursor]는 앞 페이지의 `nextCursor`다. */
    fun findPage(broadcastId: Long, cursor: Long?): CommentPage
}

/** 삭제된 댓글([deleted])은 작성자와 본문을 가려 [authorId], [authorNickname], [content]가 null이다. */
data class CommentResult(
    val id: Long,
    val authorId: Long?,
    val authorNickname: String?,
    val content: String?,
    val createdAt: LocalDateTime,
    val edited: Boolean,
    val deleted: Boolean,
)

/** [count]는 삭제된 댓글을 뺀 방송의 댓글 수다. [nextCursor]가 null이면 마지막 페이지다. */
data class CommentPage(
    val items: List<CommentResult>,
    val nextCursor: Long?,
    val count: Long,
)
