package com.maruhxn.fflux.comment.domain

import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 다시보기에 단 댓글. 작성자 닉네임은 작성 시점의 값을 남긴다.
 * 수정·삭제는 작성자만 한다(스트리머도 못 한다). 삭제는 soft delete라 목록에 자리가 남는다.
 * 답글은 원댓글을 부모로 가진 댓글이고 한 단계만 둔다(답글에는 답글을 달 수 없다).
 */
@Entity
@Table(name = "comment")
class Comment protected constructor(
    broadcastId: Long,
    parentId: Long?,
    authorId: Long,
    authorNickname: String,
    content: CommentContent,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val broadcastId: Long = broadcastId

    /** 답글이면 원댓글 ID, 원댓글이면 null. */
    val parentId: Long? = parentId

    @Column(nullable = false)
    val authorId: Long = authorId

    @Column(nullable = false)
    val authorNickname: String = authorNickname

    @Embedded
    var content: CommentContent = content
        protected set

    @Column(nullable = false)
    val createdAt: LocalDateTime = createdAt

    /** 마지막으로 수정한 시각. 수정한 적 없으면 null. */
    var updatedAt: LocalDateTime? = null
        protected set

    /** 작성자가 지운 시각. 지운 댓글은 목록에 자리만 남는다. */
    var deletedAt: LocalDateTime? = null
        protected set

    /** 방송이 삭제되어 숨긴 시각. 숨긴 댓글은 어디에도 보이지 않는다. 한 번만 채우는 값이라 조건부 UPDATE로 쓴다. */
    var hiddenAt: LocalDateTime? = null
        protected set

    fun isEdited(): Boolean = updatedAt != null

    fun isDeleted(): Boolean = deletedAt != null

    fun edit(memberId: Long, content: CommentContent, now: LocalDateTime) {
        checkAuthor(memberId)
        if (isDeleted()) throw ConflictException("삭제된 댓글은 수정할 수 없습니다.")
        this.content = content
        updatedAt = now
    }

    fun delete(memberId: Long, now: LocalDateTime) {
        checkAuthor(memberId)
        if (isDeleted()) throw ConflictException("이미 삭제된 댓글입니다.")
        deletedAt = now
    }

    private fun checkAuthor(memberId: Long) {
        if (memberId != authorId) throw ForbiddenException("댓글 작성자만 할 수 있습니다.")
    }

    companion object {
        fun write(broadcastId: Long, authorId: Long, authorNickname: String, content: CommentContent, now: LocalDateTime) =
            Comment(broadcastId, null, authorId, authorNickname, content, now)

        /** [parent]에 다는 답글. 같은 방송에 달리고, 답글이나 삭제된 댓글에는 달 수 없다(409). */
        fun reply(parent: Comment, authorId: Long, authorNickname: String, content: CommentContent, now: LocalDateTime): Comment {
            if (parent.parentId != null) throw ConflictException("답글에는 답글을 달 수 없습니다.")
            if (parent.isDeleted()) throw ConflictException("삭제된 댓글에는 답글을 달 수 없습니다.")
            return Comment(parent.broadcastId, parent.id, authorId, authorNickname, content, now)
        }
    }
}
