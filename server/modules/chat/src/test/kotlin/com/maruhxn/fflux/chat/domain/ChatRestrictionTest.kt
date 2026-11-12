package com.maruhxn.fflux.chat.domain

import com.maruhxn.fflux.common.error.ConflictException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatRestrictionTest {

    private val now = LocalDateTime.of(2026, 11, 10, 21, 0)

    @Test
    fun `30초 금지는 30초 동안만 금지 중이다`() {
        val restriction = ChatRestriction.restrict(streamerId = 10, memberId = 20, memberNickname = "시청자", ChatRestrictionDuration.THIRTY_SECONDS, now)

        assertEquals(LocalDateTime.of(2026, 11, 10, 21, 0, 30), restriction.until)
        assertTrue(restriction.isActive(now.plusSeconds(29)))
        assertFalse(restriction.isActive(now.plusSeconds(30)))
    }

    @Test
    fun `1시간 금지는 1시간 뒤에 풀린다`() {
        val restriction = ChatRestriction.restrict(10, 20, "시청자", ChatRestrictionDuration.ONE_HOUR, now)

        assertEquals(LocalDateTime.of(2026, 11, 10, 22, 0), restriction.until)
    }

    @Test
    fun `풀 때까지 금지는 기한이 없다`() {
        val restriction = ChatRestriction.restrict(10, 20, "시청자", ChatRestrictionDuration.UNTIL_LIFTED, now)

        assertNull(restriction.until)
        assertTrue(restriction.isActive(now.plusYears(10)))
    }

    @Test
    fun `다시 금지하면 마지막 선택으로 덮어쓴다`() {
        val restriction = ChatRestriction.restrict(10, 20, "시청자", ChatRestrictionDuration.UNTIL_LIFTED, now)

        restriction.restrictAgain("새닉네임", ChatRestrictionDuration.THIRTY_SECONDS, now.plusMinutes(1))

        assertEquals(now.plusMinutes(1).plusSeconds(30), restriction.until)
        assertEquals("새닉네임", restriction.memberNickname)
    }

    @Test
    fun `스트리머는 자기 자신을 금지할 수 없다`() {
        assertFailsWith<ConflictException> { ChatRestriction.restrict(10, 10, "스트리머", ChatRestrictionDuration.THIRTY_SECONDS, now) }
    }
}
