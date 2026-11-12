package com.maruhxn.fflux.chat.domain

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val chat = Chat.post(1, live = true, restriction = null, authorId = 20, authorNickname = "시청자", content = ChatContent.of("안녕"), now = now)

        assertEquals("시청자", chat.authorNickname)
        assertFailsWith<ConflictException> { Chat.post(1, live = false, restriction = null, authorId = 20, authorNickname = "시청자", content = ChatContent.of("안녕"), now = now) }
    }

    @Test
    fun `채팅 금지 중이면 LIVE가 아니어도 409보다 403이 먼저고 메시지에 날짜와 시각을 담는다`() {
        val now = LocalDateTime.of(2026, 11, 10, 23, 59)
        val oneHour = ChatRestriction.restrict(streamerId = 10, memberId = 20, memberNickname = "시청자", ChatRestrictionDuration.ONE_HOUR, now)
        val untilLifted = ChatRestriction.restrict(10, 20, "시청자", ChatRestrictionDuration.UNTIL_LIFTED, now)

        val e = assertFailsWith<ForbiddenException> { Chat.post(1, live = false, restriction = oneHour, authorId = 20, authorNickname = "시청자", content = ChatContent.of("안녕"), now = now) }
        assertEquals("11월 11일 00:59:00까지 채팅이 금지되었습니다.", e.message)
        assertFailsWith<ForbiddenException> { Chat.post(1, live = true, restriction = untilLifted, authorId = 20, authorNickname = "시청자", content = ChatContent.of("안녕"), now = now) }
        // 기간이 지난 금지는 금지가 아니다.
        Chat.post(1, live = true, restriction = oneHour, authorId = 20, authorNickname = "시청자", content = ChatContent.of("안녕"), now = now.plusHours(1))
    }

    @Test
    fun `방송의 스트리머만 채팅을 가리고 이미 가린 채팅은 그대로 둔다`() {
        val now = LocalDateTime.of(2026, 11, 10, 21, 0)
        val chat = Chat.post(1, live = true, restriction = null, authorId = 20, authorNickname = "시청자", content = ChatContent.of("욕설"), now = now)

        assertFailsWith<ForbiddenException> { chat.hide(memberId = 30, streamerId = 10, now = now) }
        assertNull(chat.hiddenAt)

        assertTrue(chat.hide(memberId = 10, streamerId = 10, now = now))
        assertEquals(now, chat.hiddenAt)
        assertFalse(chat.hide(memberId = 10, streamerId = 10, now = now.plusMinutes(1)))
        assertEquals(now, chat.hiddenAt)
    }

    @Test
    fun `방송의 스트리머만 채팅 작성자를 금지하고 스트리머가 아니면 자기 채팅이어도 409보다 403이 먼저다`() {
        val now = LocalDateTime.of(2026, 11, 10, 21, 0)
        val viewerChat = Chat.post(1, live = true, restriction = null, authorId = 20, authorNickname = "시청자", content = ChatContent.of("도배"), now = now)
        val streamerChat = Chat.post(1, live = true, restriction = null, authorId = 10, authorNickname = "스트리머", content = ChatContent.of("공지"), now = now)

        assertFailsWith<ForbiddenException> { viewerChat.restrictAuthor(memberId = 30, streamerId = 10, current = null, ChatRestrictionDuration.ONE_HOUR, now) }
        assertFailsWith<ForbiddenException> { streamerChat.restrictAuthor(memberId = 30, streamerId = 10, current = null, ChatRestrictionDuration.ONE_HOUR, now) }
        assertFailsWith<ConflictException> { streamerChat.restrictAuthor(memberId = 10, streamerId = 10, current = null, ChatRestrictionDuration.ONE_HOUR, now) }

        val restriction = viewerChat.restrictAuthor(memberId = 10, streamerId = 10, current = null, ChatRestrictionDuration.ONE_HOUR, now)
        assertEquals(20, restriction.memberId)
        assertEquals("시청자", restriction.memberNickname)

        val again = viewerChat.restrictAuthor(memberId = 10, streamerId = 10, current = restriction, ChatRestrictionDuration.UNTIL_LIFTED, now.plusMinutes(1))
        assertTrue(again === restriction)
        assertNull(again.until)
        assertEquals(now.plusMinutes(1), again.restrictedAt)
    }
}
