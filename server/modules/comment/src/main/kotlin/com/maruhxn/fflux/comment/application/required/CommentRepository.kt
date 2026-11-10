package com.maruhxn.fflux.comment.application.required

import com.maruhxn.fflux.comment.domain.Comment
import java.time.LocalDateTime

/** 숨긴 댓글(방송이 삭제됨)은 모든 조회에서 없는 것으로 본다. */
interface CommentRepository {
    fun save(comment: Comment): Comment

    /** 숨기지 않은 댓글을 행 잠금(`SELECT … FOR UPDATE`)으로 읽는다. */
    fun findForUpdateById(id: Long): Comment?

    /** 방송의 숨기지 않은 댓글(삭제된 댓글 포함)을 ID 내림차순으로 [limit]개까지 읽는다. [cursor]가 있으면 그보다 작은 ID만 읽는다. */
    fun findPage(broadcastId: Long, cursor: Long?, limit: Int): List<Comment>

    /** 방송의 숨기지 않았고 삭제되지 않은 댓글 수. */
    fun countVisible(broadcastId: Long): Long

    /** 방송의 아직 숨기지 않은 댓글을 모두 숨기고, 바뀐 행 수를 돌려준다. */
    fun hideAll(broadcastId: Long, now: LocalDateTime): Int
}
