package com.maruhxn.fflux.comment.application.provided

/** 댓글 작성·수정·삭제. 수정·삭제는 작성자만 한다. */
interface CommentModifier {
    /** 다시보기(ENDED + READY 녹화)에만 쓴다. 없거나 삭제된 방송은 404, 다시보기가 아니면 409. 만든 댓글 ID를 돌려준다. */
    fun write(command: WriteCommentCommand): Long

    /** [ReplyCommand.parentId] 원댓글에 답글을 단다. 원댓글이 없거나 숨겨졌으면 404, 답글에 답글을 달면 409. 만든 답글 ID를 돌려준다. */
    fun reply(command: ReplyCommand): Long

    /** 없으면 404, 작성자가 아니면 403, 삭제된 댓글이면 409. */
    fun edit(command: EditCommentCommand)

    /** soft delete. 없으면 404, 작성자가 아니면 403, 이미 삭제됐으면 409. */
    fun delete(commentId: Long, memberId: Long)

    /** 방송이 삭제되어 그 댓글을 모두 숨긴다. */
    fun hideAll(broadcastId: Long)
}

/** [authorNickname]은 작성 시점의 닉네임으로 남긴다. */
data class WriteCommentCommand(
    val broadcastId: Long,
    val authorId: Long,
    val authorNickname: String,
    val content: String,
)

data class ReplyCommand(
    val parentId: Long,
    val authorId: Long,
    val authorNickname: String,
    val content: String,
)

data class EditCommentCommand(
    val commentId: Long,
    val memberId: Long,
    val content: String,
)
