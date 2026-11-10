package com.maruhxn.fflux.comment.port.persistence

import com.maruhxn.fflux.comment.domain.Comment

interface CommentRepositoryCustom {
    fun findPage(broadcastId: Long, cursor: Long?, limit: Int): List<Comment>
}
