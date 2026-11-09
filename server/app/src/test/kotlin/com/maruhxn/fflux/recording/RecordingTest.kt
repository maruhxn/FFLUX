package com.maruhxn.fflux.recording

import com.maruhxn.fflux.broadcast.BroadcastFlowTest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 녹화: MediaMTX 세그먼트 처리 훅의 통지(`/internal/mediamtx/recordings/...`)와 방송 상세·다시보기 목록의 노출.
 * 테스트가 MediaMTX 대신 통지를 직접 부른다.
 */
class RecordingTest : BroadcastFlowTest() {

    // 통지와 상태 전이

    @Test
    fun `처리 시작 통지를 받으면 스트리머에게만 처리 중 녹화가 보인다`() {
        val id = endRecorded(register(streamer))

        assertEquals(HttpStatus.OK, processing(id).statusCode)

        assertEquals(mapOf("status" to "PROCESSING", "url" to null, "durationSeconds" to null), recordingOf(id, streamer))
        assertNull(recordingOf(id, other))
        assertNull(recordingOf(id, null))
    }

    @Test
    fun `완료 통지를 받으면 READY 녹화가 모두에게 재생 주소와 길이로 보인다`() {
        val id = endRecorded(register(streamer))
        processing(id)

        assertEquals(HttpStatus.OK, ready(id, duration = 3600.4).statusCode)

        val expected = mapOf("status" to "READY", "url" to "/vod/${fileOf(id)}", "durationSeconds" to 3600)
        listOf(streamer, other, null).forEach { assertEquals(expected, recordingOf(id, it)) }
    }

    @Test
    fun `실패 통지를 받으면 스트리머에게만 실패한 녹화가 보인다`() {
        val id = endRecorded(register(streamer))
        processing(id)

        assertEquals(HttpStatus.OK, failed(id).statusCode)

        assertEquals("FAILED", recordingOf(id, streamer)?.get("status"))
        assertNull(recordingOf(id, other))
        assertNull(recordingOf(id, null))
    }

    @Test
    fun `같은 통지가 반복돼도 녹화는 하나이고 결과가 같다`() {
        val id = endRecorded(register(streamer))

        repeat(2) { assertEquals(HttpStatus.OK, processing(id).statusCode) }
        repeat(2) { assertEquals(HttpStatus.OK, ready(id, duration = 60.0).statusCode) }
        assertEquals(HttpStatus.OK, failed(id).statusCode)

        assertEquals("READY", recordingOf(id, null)?.get("status"))
        assertEquals(1, recordingCount(id))
    }

    @Test
    fun `완료 통지가 처리 시작보다 먼저 와도 READY다`() {
        val id = endRecorded(register(streamer))

        ready(id, duration = 60.0)
        processing(id)

        assertEquals("READY", recordingOf(id, null)?.get("status"))
    }

    @Test
    fun `두 번째 세그먼트의 통지는 무시한다`() {
        val id = endRecorded(register(streamer))
        processing(id)
        ready(id, duration = 60.0)

        assertEquals(HttpStatus.OK, processing(id, SECOND).statusCode)
        assertEquals(HttpStatus.OK, ready(id, duration = 30.0, segment = SECOND).statusCode)

        assertEquals(
            mapOf("status" to "READY", "url" to "/vod/${fileOf(id)}", "durationSeconds" to 60),
            recordingOf(id, null),
        )
        assertEquals(1, recordingCount(id))
    }

    @Test
    fun `길이를 받지 못하면 방송 시작부터 종료까지의 시간으로 대신한다`() {
        val id = goLiveRecorded(register(streamer))
        clock.advance(Duration.ofSeconds(90))
        end(id, streamer)

        ready(id, duration = null)

        assertEquals(90, recordingOf(id, null)?.get("durationSeconds"))
    }

    @Test
    fun `모르는 경로나 삭제된 방송의 통지는 200이고 녹화를 만들지 않는다`() {
        val id = endRecorded(register(streamer))
        delete(id, streamer)

        assertEquals(HttpStatus.OK, processing(id).statusCode)
        assertEquals(HttpStatus.OK, notify("ready", "rec/9999", SEGMENT, "rec/9999/a.mp4", 1.0).statusCode)

        assertEquals(0, recordingCount(id))
    }

    // 다시보기 목록

    @Test
    fun `다시보기 목록은 비로그인도 보고 READY 녹화가 있는 끝난 방송만 최신순으로 담는다`() {
        val replay = endRecorded(register(streamer)).also { ready(it, duration = 60.0) }
        endRecorded(register(streamer)).also { processing(it) }
        endRecorded(register(streamer)).also { failed(it) }
        endRecorded(register(streamer))
        val latest = endRecorded(register(streamer)).also { ready(it, duration = 120.0) }

        val page = replays(null)

        assertEquals(HttpStatus.OK, page.statusCode)
        val items = page.body!!["items"] as List<Map<String, Any?>>
        assertEquals(listOf(latest, replay), items.map { (it["broadcastId"] as Number).toLong() })
        assertEquals("방송", items[0]["title"])
        assertEquals("테스트스트리머", items[0]["streamerNickname"])
        assertEquals(120, items[0]["durationSeconds"])
        assertNull(page.body!!["nextCursor"])
    }

    @Test
    fun `다시보기 목록은 커서로 20개씩 이어서 읽는다`() {
        val ids = (1..21).map { endRecorded(register(streamer)).also { id -> ready(id, duration = 1.0) } }

        val first = replays(null).body!!
        assertEquals(20, (first["items"] as List<*>).size)
        val cursor = (first["nextCursor"] as Number).toLong()

        val second = replays(cursor).body!!
        assertEquals(listOf(ids.first()), (second["items"] as List<Map<String, Any?>>).map { (it["broadcastId"] as Number).toLong() })
        assertNull(second["nextCursor"])
    }

    // 방송 삭제

    @Test
    fun `방송을 삭제하면 다시보기가 목록과 상세에서 사라지고 녹화 행은 남는다`() {
        val id = endRecorded(register(streamer)).also { ready(it, duration = 60.0) }

        assertEquals(HttpStatus.NO_CONTENT, delete(id, streamer).statusCode)

        assertEquals(emptyList<Any>(), replays(null).body!!["items"])
        assertEquals(HttpStatus.NOT_FOUND, detail(id, null).statusCode)
        assertEquals(1, recordingCount(id))
        assertEquals(
            1,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recording WHERE broadcast_id = ? AND hidden_at IS NOT NULL", Int::class.java, id),
        )
    }

    // 헬퍼

    /** 녹화를 켜고 송출(`rec/{id}`)해 LIVE로 만든다. */
    private fun goLiveRecorded(id: Long): Long {
        val token = tokenOf(issue(id, streamer, record = true))
        assertEquals(HttpStatus.OK, auth("publish", "rec/$id", token).statusCode)
        available("rec/$id")
        return id
    }

    private fun endRecorded(id: Long): Long = goLiveRecorded(id).also { unavailable("rec/$it") }

    private fun segmentOf(id: Long, name: String = SEGMENT) = "/recordings/rec/$id/$name"

    private fun fileOf(id: Long, name: String = SEGMENT) = "rec/$id/$name"

    private fun processing(id: Long, segment: String = SEGMENT) =
        notify("processing", "rec/$id", segmentOf(id, segment))

    private fun ready(id: Long, duration: Double?, segment: String = SEGMENT) =
        notify("ready", "rec/$id", segmentOf(id, segment), fileOf(id, segment), duration)

    private fun failed(id: Long) = notify("failed", "rec/$id", segmentOf(id))

    /** 훅 스크립트(`infra/hooks/remux-recording.sh`)가 보내는 본문 형태 그대로 보낸다. */
    private fun notify(
        event: String,
        path: String,
        segmentPath: String,
        filePath: String? = null,
        durationSeconds: Double? = null,
    ): ResponseEntity<Void> =
        http.post().uri("/internal/mediamtx/recordings/{event}", event)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                buildMap {
                    put("path", path)
                    put("segmentPath", segmentPath)
                    filePath?.let { put("filePath", it) }
                    durationSeconds?.let { put("durationSeconds", it) }
                },
            )
            .retrieve().toBodilessEntity()

    @Suppress("UNCHECKED_CAST")
    private fun detail(id: Long, cookie: String?): ResponseEntity<Map<String, Any?>> =
        http.get().uri("/api/broadcasts/{id}", id).withCookie(cookie)
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun recordingOf(id: Long, cookie: String?): Map<String, Any?>? {
        val response = detail(id, cookie)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!["recording"] as Map<String, Any?>?
    }

    @Suppress("UNCHECKED_CAST")
    private fun replays(cursor: Long?): ResponseEntity<Map<String, Any?>> =
        http.get().uri { it.path("/api/replays").queryParamIfPresent("cursor", java.util.Optional.ofNullable(cursor)).build() }
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    private fun recordingCount(broadcastId: Long): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recording WHERE broadcast_id = ?", Int::class.java, broadcastId)!!

    companion object {
        private const val SEGMENT = "2026-11-10_21-00-00-000000.mp4"
        private const val SECOND = "2026-11-11_09-00-00-000000.mp4"
    }
}
