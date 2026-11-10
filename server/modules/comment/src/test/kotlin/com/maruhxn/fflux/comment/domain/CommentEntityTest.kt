package com.maruhxn.fflux.comment.domain

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentEntityTest {

    private val now = LocalDateTime.of(2026, 11, 10, 21, 0)

    private fun write() = Comment.write(BROADCAST, AUTHOR, "작성자", CommentContent.of("좋은 방송"), now)

    @Test
    fun `본문은 앞뒤 공백을 지운다`() {
        assertEquals("안녕", CommentContent.of("  안녕 \n").value)
    }

    @Test
    fun `빈 본문과 공백만 있는 본문은 content 칸의 400`() {
        listOf("", "   \n").forEach { raw ->
            val e = assertFailsWith<BadRequestException> { CommentContent.of(raw) }
            assertEquals(setOf("content"), e.errors.keys)
        }
    }

    @Test
    fun `본문은 500자까지 받는다`() {
        assertEquals(500, CommentContent.of("가".repeat(500)).value.length)
        assertFailsWith<BadRequestException> { CommentContent.of("가".repeat(501)) }
    }

    @Test
    fun `새 댓글은 작성자 닉네임을 남기고 수정되지 않았다`() {
        val comment = write()

        assertEquals("작성자", comment.authorNickname)
        assertEquals("좋은 방송", comment.content.value)
        assertFalse(comment.isEdited())
    }

    @Test
    fun `작성자가 고치면 본문이 바뀌고 수정됨이 된다`() {
        val comment = write()

        comment.edit(AUTHOR, CommentContent.of("고친 내용"), now.plusMinutes(1))

        assertEquals("고친 내용", comment.content.value)
        assertTrue(comment.isEdited())
    }

    @Test
    fun `작성자가 아니면 수정과 삭제는 403`() {
        val comment = write()

        assertFailsWith<ForbiddenException> { comment.edit(OTHER, CommentContent.of("남의 댓글"), now) }
        assertFailsWith<ForbiddenException> { comment.delete(OTHER, now) }
        assertEquals("좋은 방송", comment.content.value)
        assertFalse(comment.isDeleted())
    }

    @Test
    fun `작성자가 지우면 삭제된 댓글이 된다`() {
        val comment = write()

        comment.delete(AUTHOR, now)

        assertTrue(comment.isDeleted())
    }

    @Test
    fun `삭제된 댓글의 수정과 재삭제는 409`() {
        val comment = write().apply { delete(AUTHOR, now) }

        assertFailsWith<ConflictException> { comment.edit(AUTHOR, CommentContent.of("되살리기"), now) }
        assertFailsWith<ConflictException> { comment.delete(AUTHOR, now) }
    }

    @Test
    fun `남의 삭제된 댓글은 409보다 403이 먼저다`() {
        val comment = write().apply { delete(AUTHOR, now) }

        assertFailsWith<ForbiddenException> { comment.edit(OTHER, CommentContent.of("되살리기"), now) }
        assertFailsWith<ForbiddenException> { comment.delete(OTHER, now) }
    }

    companion object {
        private const val BROADCAST = 1L
        private const val AUTHOR = 10L
        private const val OTHER = 20L
    }
}
