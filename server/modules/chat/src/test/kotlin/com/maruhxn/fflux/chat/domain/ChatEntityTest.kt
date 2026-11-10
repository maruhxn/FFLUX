package com.maruhxn.fflux.chat.domain

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.ConflictException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChatEntityTest {

    @Test
    fun `채팅 본문은 앞뒤 공백을 지운다`() {
        assertEquals("안녕하세요", ChatContent.of("  안녕하세요 \n").value)
    }

    @Test
    fun `채팅 본문은 1자부터 200자까지 받는다`() {
        assertEquals(1, ChatContent.of("가").value.length)
        assertEquals(200, ChatContent.of("가".repeat(200)).value.length)
        listOf("", "   ", "가".repeat(201)).forEach { raw ->
            val e = assertFailsWith<BadRequestException> { ChatContent.of(raw) }
            assertEquals(setOf("content"), e.errors.keys)
        }
    }

    @Test
    fun `LIVE 방송에만 채팅할 수 있고 아니면 409`() {
        val now = LocalDateTime.of(2026, 11, 10, 21, 0)
        val chat = Chat.post(1, live = true, authorId = 20, authorNickname = "시청자", content = ChatContent.of("안녕"), now = now)

        assertEquals("시청자", chat.authorNickname)
        assertFailsWith<ConflictException> { Chat.post(1, live = false, authorId = 20, authorNickname = "시청자", content = ChatContent.of("안녕"), now = now) }
    }
}
