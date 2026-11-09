package com.maruhxn.fflux.recording.domain

import com.maruhxn.fflux.common.error.ConflictException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingEntityTest {

    private val now = LocalDateTime.of(2026, 11, 10, 21, 0)

    private fun processing() = Recording.processing(BROADCAST, STREAMER, SEGMENT, now)

    private fun ready() = processing().apply { complete(SEGMENT, FILE, 3600) }

    private fun failed() = processing().apply { fail(SEGMENT) }

    @Test
    fun `처리 시작 통지로 만든 녹화는 PROCESSING이고 파일이 없다`() {
        val recording = processing()

        assertEquals(RecordingStatus.PROCESSING, recording.status)
        assertNull(recording.filePath)
        assertNull(recording.durationSeconds)
    }

    @Test
    fun `PROCESSING 녹화가 완료되면 READY가 되고 파일과 길이를 남긴다`() {
        val recording = processing()

        assertTrue(recording.complete(SEGMENT, FILE, 3600))

        assertEquals(RecordingStatus.READY, recording.status)
        assertEquals(FILE, recording.filePath)
        assertEquals(3600, recording.durationSeconds)
    }

    @Test
    fun `PROCESSING 녹화가 실패하면 FAILED가 된다`() {
        val recording = processing()

        assertTrue(recording.fail(SEGMENT))

        assertEquals(RecordingStatus.FAILED, recording.status)
    }

    @Test
    fun `완료 통지가 처리 시작보다 먼저 오면 바로 READY로 만들고 늦은 처리 시작은 무시한다`() {
        val recording = Recording.ready(BROADCAST, STREAMER, SEGMENT, FILE, 3600, now)

        assertFalse(recording.startProcessing(SEGMENT))

        assertEquals(RecordingStatus.READY, recording.status)
    }

    @Test
    fun `같은 통지가 반복되면 바꾸지 않는다`() {
        assertFalse(processing().startProcessing(SEGMENT))

        val ready = ready()
        assertFalse(ready.complete(SEGMENT, "rec/1/other.mp4", 10))
        assertEquals(FILE, ready.filePath)
        assertEquals(3600, ready.durationSeconds)

        assertFalse(failed().fail(SEGMENT))
    }

    @Test
    fun `READY 녹화는 실패 통지로 되돌리지 않는다`() {
        val recording = ready()

        assertFalse(recording.fail(SEGMENT))

        assertEquals(RecordingStatus.READY, recording.status)
    }

    @Test
    fun `FAILED 녹화를 손으로 다시 처리해 완료되면 READY가 된다`() {
        val recording = failed()

        assertFalse(recording.startProcessing(SEGMENT))
        assertTrue(recording.complete(SEGMENT, FILE, 3600))

        assertEquals(RecordingStatus.READY, recording.status)
    }

    @Test
    fun `다른 세그먼트의 통지는 두 번째 세그먼트라 409이고 녹화를 바꾸지 않는다`() {
        val recording = processing()

        assertFailsWith<ConflictException> { recording.startProcessing(SECOND_SEGMENT) }
        assertFailsWith<ConflictException> { recording.complete(SECOND_SEGMENT, "rec/1/second.mp4", 10) }
        assertFailsWith<ConflictException> { recording.fail(SECOND_SEGMENT) }

        assertEquals(RecordingStatus.PROCESSING, recording.status)
        assertNull(recording.filePath)
    }

    @Test
    fun `READY 녹화는 모두에게 보이고 PROCESSING과 FAILED는 스트리머에게만 보인다`() {
        assertTrue(ready().isVisibleTo(null))
        assertTrue(ready().isVisibleTo(OTHER))

        listOf(processing(), failed()).forEach {
            assertFalse(it.isVisibleTo(null))
            assertFalse(it.isVisibleTo(OTHER))
            assertTrue(it.isVisibleTo(STREAMER))
        }
    }

    companion object {
        private const val BROADCAST = 1L
        private const val STREAMER = 10L
        private const val OTHER = 20L
        private const val SEGMENT = "/recordings/rec/1/2026-11-10_21-00-00-000000.mp4"
        private const val SECOND_SEGMENT = "/recordings/rec/1/2026-11-11_09-00-00-000000.mp4"
        private const val FILE = "rec/1/2026-11-10_21-00-00-000000.mp4"
    }
}
