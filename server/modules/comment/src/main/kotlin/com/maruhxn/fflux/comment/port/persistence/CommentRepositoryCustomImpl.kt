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
                comment.parentId.isNull,
                comment.hiddenAt.isNull,
                cursor?.let { comment.id.lt(it) },
            )
            .orderBy(comment.id.desc())
            .limit(limit.toLong())
            .fetch()

    override fun findReplies(parentId: Long, cursor: Long?, limit: Int): List<Comment> =
        queryFactory.selectFrom(comment)
            .where(
                comment.parentId.eq(parentId),
                comment.hiddenAt.isNull,
                cursor?.let { comment.id.gt(it) },
            )
            .orderBy(comment.id.asc())
            .limit(limit.toLong())
            .fetch()

    override fun countVisibleReplies(parentIds: Collection<Long>): Map<Long, Long> {
        if (parentIds.isEmpty()) return emptyMap()
        return queryFactory.select(comment.parentId, comment.count())
            .from(comment)
            .where(
                comment.parentId.`in`(parentIds),
                comment.deletedAt.isNull,
                comment.hiddenAt.isNull,
            )
            .groupBy(comment.parentId)
            .fetch()
            .mapNotNull { row -> row.get(comment.parentId)?.let { parentId -> parentId to (row.get(comment.count()) ?: 0) } }
            .toMap()
    }
}
