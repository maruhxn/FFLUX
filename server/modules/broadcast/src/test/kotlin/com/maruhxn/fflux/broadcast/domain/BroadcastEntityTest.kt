package com.maruhxn.fflux.broadcast.domain

import com.maruhxn.fflux.common.error.ForbiddenException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** LIVE·ENDED 조합은 전이 메서드가 생기는 #6·#8에서 추가한다. 지금은 통합 테스트가 맡는다. */
class BroadcastEntityTest {

    private val now = LocalDateTime.of(2026, 11, 6, 21, 0)

    private fun register() =
        Broadcast.register(STREAMER, "스트리머", BroadcastTitle("원래 제목"), null, now)

    @Test
    fun `등록하면 READY 방송이고 스트리머 닉네임을 그대로 담는다`() {
        val broadcast = register()

        assertEquals(BroadcastStatus.READY, broadcast.status)
        assertEquals(STREAMER, broadcast.streamerId)
        assertEquals("스트리머", broadcast.streamerNickname)
    }

    @Test
    fun `스트리머는 READY 방송을 수정한다`() {
        val broadcast = register()

        broadcast.edit(STREAMER, BroadcastTitle("바꾼 제목"), BroadcastDescription.of("설명"))

        assertEquals(BroadcastTitle("바꾼 제목"), broadcast.title)
        assertEquals("설명", broadcast.description?.value)
    }

    @Test
    fun `스트리머가 아니면 수정할 수 없다`() {
        val broadcast = register()

        assertFailsWith<ForbiddenException> { broadcast.edit(OTHER, BroadcastTitle("바꾼 제목"), null) }
        assertEquals(BroadcastTitle("원래 제목"), broadcast.title)
    }

    @Test
    fun `스트리머는 READY 방송을 삭제한다`() {
        val broadcast = register()

        broadcast.delete(STREAMER, now)

        assertEquals(now, broadcast.deletedAt)
    }

    @Test
    fun `스트리머가 아니면 삭제할 수 없다`() {
        val broadcast = register()

        assertFailsWith<ForbiddenException> { broadcast.delete(OTHER, now) }
        assertNull(broadcast.deletedAt)
    }

    companion object {
        const val STREAMER = 1L
        const val OTHER = 2L
    }
}
