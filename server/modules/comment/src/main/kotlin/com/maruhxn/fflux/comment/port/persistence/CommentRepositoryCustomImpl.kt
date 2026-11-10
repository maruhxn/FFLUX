package com.maruhxn.fflux.comment.port.persistence

import com.maruhxn.fflux.comment.domain.Comment
import com.maruhxn.fflux.comment.domain.QComment.Companion.comment
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager

class CommentRepositoryCustomImpl(entityManager: EntityManager) : CommentRepositoryCustom {

    private val queryFactory = JPAQueryFactory(entityManager)

    override fun findPage(broadcastId: Long, cursor: Long?, limit: Int): List<Comment> =
        queryFactory.selectFrom(comment)
            .where(
                comment.broadcastId.eq(broadcastId),
                comment.hiddenAt.isNull,
                cursor?.let { comment.id.lt(it) },
            )
            .orderBy(comment.id.desc())
            .limit(limit.toLong())
            .fetch()
}
