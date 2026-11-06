package com.maruhxn.fflux.broadcast

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** LIVE 방송 시청: 상세가 알려 주는 WHEP·HLS 주소와 MediaMTX `read` 인증. */
class WatchTest : BroadcastFlowTest() {

    @Test
    fun `LIVE 방송 상세는 비로그인에게도 WHEP와 HLS 주소를 알려 준다`() {
        val id = goLive(register(streamer))

        val body = detail(id, null).body!!

        assertEquals("/media/live/$id/whep", body["whepUrl"])
        assertEquals("/media/live/$id/index.m3u8", body["hlsUrl"])
    }

    @Test
    fun `녹화하는 방송은 rec 경로의 주소를 알려 준다`() {
        val id = register(streamer)
        auth("publish", "rec/$id", tokenOf(issue(id, streamer, record = true)))
        available("rec/$id")

        assertEquals("/media/rec/$id/whep", detail(id, null).body!!["whepUrl"])
    }

    @Test
    fun `READY나 ENDED 방송 상세에는 시청 주소가 없다`() {
        val ready = register(streamer).also { issue(it, streamer) }
        val ended = goLive(register(streamer)).also { end(it, streamer) }

        listOf(ready, ended).forEach { id ->
            val body = detail(id, null).body!!
            assertNull(body["whepUrl"])
            assertNull(body["hlsUrl"])
        }
    }

    @Test
    fun `READY ENDED 삭제된 방송의 read는 거절하고 LIVE 방송만 허용한다`() {
        val ready = register(streamer).also { issue(it, streamer) }
        val ended = goLive(register(streamer)).also { end(it, streamer) }
        val deleted = goLive(register(streamer)).also { end(it, streamer); delete(it, streamer) }
        val live = goLive(register(other), other)

        listOf(ready, ended, deleted).forEach { id ->
            assertEquals(HttpStatus.UNAUTHORIZED, auth("read", "live/$id", null).statusCode, "방송 $id")
        }
        assertEquals(HttpStatus.OK, auth("read", "live/$live", null).statusCode)
    }

    @Suppress("UNCHECKED_CAST")
    private fun detail(id: Long, cookie: String?): ResponseEntity<Map<String, Any?>> =
        http.get().uri("/api/broadcasts/{id}", id).withCookie(cookie)
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>
}
