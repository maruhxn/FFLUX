package com.maruhxn.fflux.broadcast.domain

import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BroadcastEntityTest {

    private val now = LocalDateTime.of(2026, 11, 6, 21, 0)

    private fun register() =
        Broadcast.register(STREAMER, "스트리머", BroadcastTitle.of("원래 제목"), null, now)

    private fun live() = register().apply { confirmPublishStarted() }

    private fun ended() = live().apply { endByDisconnect() }

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

        broadcast.edit(STREAMER, BroadcastTitle.of("바꾼 제목"), BroadcastDescription.of("설명"))

        assertEquals(BroadcastTitle.of("바꾼 제목"), broadcast.title)
        assertEquals("설명", broadcast.description?.value)
    }

    @Test
    fun `스트리머가 아니면 수정할 수 없다`() {
        val broadcast = register()

        assertFailsWith<ForbiddenException> { broadcast.edit(OTHER, BroadcastTitle.of("바꾼 제목"), null) }
        assertEquals(BroadcastTitle.of("원래 제목"), broadcast.title)
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

    // 수정·삭제 × LIVE·ENDED

    @Test
    fun `스트리머는 LIVE 방송을 수정한다`() {
        val broadcast = live()

        broadcast.edit(STREAMER, BroadcastTitle.of("바꾼 제목"), null)

        assertEquals(BroadcastTitle.of("바꾼 제목"), broadcast.title)
    }

    @Test
    fun `ENDED 방송은 수정할 수 없다`() {
        val broadcast = ended()

        assertFailsWith<ConflictException> { broadcast.edit(STREAMER, BroadcastTitle.of("바꾼 제목"), null) }
    }

    @Test
    fun `LIVE 방송은 삭제할 수 없다`() {
        val broadcast = live()

        assertFailsWith<ConflictException> { broadcast.delete(STREAMER, now) }
        assertNull(broadcast.deletedAt)
    }

    @Test
    fun `스트리머는 ENDED 방송을 삭제한다`() {
        val broadcast = ended()

        broadcast.delete(STREAMER, now)

        assertEquals(now, broadcast.deletedAt)
    }

    @Test
    fun `타인은 상태와 상관없이 수정·삭제가 403이다`() {
        listOf(live(), ended()).forEach { broadcast ->
            assertFailsWith<ForbiddenException> { broadcast.edit(OTHER, BroadcastTitle.of("바꾼 제목"), null) }
            assertFailsWith<ForbiddenException> { broadcast.delete(OTHER, now) }
        }
    }

    // 송출 준비

    @Test
    fun `녹화를 고르면 rec 경로 고르지 않으면 live 경로로 송출을 준비한다`() {
        val recorded = register().apply { preparePublish(STREAMER, record = true) }
        val unrecorded = register().apply { preparePublish(STREAMER, record = false) }

        assertTrue(recorded.recordingEnabled)
        assertEquals("rec/${recorded.id}", recorded.mediaPath)
        assertFalse(unrecorded.recordingEnabled)
        assertEquals("live/${unrecorded.id}", unrecorded.mediaPath)
    }

    @Test
    fun `송출 준비를 다시 하면 마지막 녹화 선택을 따른다`() {
        val broadcast = register()

        broadcast.preparePublish(STREAMER, record = true)
        broadcast.preparePublish(STREAMER, record = false)

        assertFalse(broadcast.recordingEnabled)
        assertEquals("live/${broadcast.id}", broadcast.mediaPath)
    }

    @Test
    fun `타인은 송출을 준비할 수 없다`() {
        val broadcast = register()

        assertFailsWith<ForbiddenException> { broadcast.preparePublish(OTHER, record = true) }
        assertNull(broadcast.mediaPath)
    }

    @Test
    fun `LIVE나 ENDED 방송은 송출을 준비할 수 없다`() {
        listOf(live(), ended()).forEach { broadcast ->
            assertFailsWith<ConflictException> { broadcast.preparePublish(STREAMER, record = true) }
        }
    }

    // 송출 시작 확인

    @Test
    fun `송출 시작을 확인하면 READY 방송이 LIVE가 된다`() {
        val broadcast = register()

        assertTrue(broadcast.confirmPublishStarted())
        assertEquals(BroadcastStatus.LIVE, broadcast.status)
    }

    @Test
    fun `이미 LIVE인 방송의 송출 시작 확인은 아무것도 바꾸지 않는다`() {
        val broadcast = live()

        assertFalse(broadcast.confirmPublishStarted())
        assertEquals(BroadcastStatus.LIVE, broadcast.status)
    }

    @Test
    fun `ENDED 방송은 송출 시작을 확인할 수 없다`() {
        val broadcast = ended()

        assertFailsWith<ConflictException> { broadcast.confirmPublishStarted() }
        assertEquals(BroadcastStatus.ENDED, broadcast.status)
    }

    // 송출 끊김

    @Test
    fun `송출이 끊기면 LIVE 방송이 ENDED가 된다`() {
        val broadcast = live()

        assertTrue(broadcast.endByDisconnect())
        assertEquals(BroadcastStatus.ENDED, broadcast.status)
    }

    @Test
    fun `이미 ENDED인 방송의 송출 끊김은 아무것도 바꾸지 않는다`() {
        val broadcast = ended()

        assertFalse(broadcast.endByDisconnect())
        assertEquals(BroadcastStatus.ENDED, broadcast.status)
    }

    @Test
    fun `READY 방송은 송출 끊김으로 끝낼 수 없다`() {
        val broadcast = register()

        assertFailsWith<ConflictException> { broadcast.endByDisconnect() }
        assertEquals(BroadcastStatus.READY, broadcast.status)
    }

    // 종료 요청

    @Test
    fun `스트리머가 종료를 요청하면 LIVE 방송이 ENDED가 된다`() {
        val broadcast = live()

        assertTrue(broadcast.requestEnd(STREAMER))
        assertEquals(BroadcastStatus.ENDED, broadcast.status)
    }

    @Test
    fun `이미 ENDED인 방송의 종료 요청은 아무것도 바꾸지 않는다`() {
        val broadcast = ended()

        assertFalse(broadcast.requestEnd(STREAMER))
        assertEquals(BroadcastStatus.ENDED, broadcast.status)
    }

    @Test
    fun `READY 방송은 종료를 요청할 수 없다`() {
        val broadcast = register()

        assertFailsWith<ConflictException> { broadcast.requestEnd(STREAMER) }
        assertEquals(BroadcastStatus.READY, broadcast.status)
    }

    @Test
    fun `타인은 상태와 상관없이 종료를 요청할 수 없다`() {
        listOf(register(), live(), ended()).forEach { broadcast ->
            val before = broadcast.status
            assertFailsWith<ForbiddenException> { broadcast.requestEnd(OTHER) }
            assertEquals(before, broadcast.status)
        }
    }

    companion object {
        const val STREAMER = 1L
        const val OTHER = 2L
    }
}
