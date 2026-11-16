package com.maruhxn.fflux.broadcast

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

/** 방송 목록 썸네일: 송출 경로가 정해진 방송은 `thumbnailer`가 찍는 장면의 주소를 알려 준다. */
class ThumbnailTest : BroadcastFlowTest() {

    @Test
    fun `송출 경로가 정해진 방송만 목록에 썸네일 주소가 있다`() {
        val ready = register(streamer)
        val prepared = register(streamer).also { issue(it, streamer, record = true) }
        val live = goLive(register(other), other)
        val ended = goLive(register(streamer)).also { end(it, streamer) }

        val mine = thumbnailsOf("/api/broadcasts/mine", streamer)
        assertEquals(null, mine[ready])
        assertEquals("/thumbnails/rec/$prepared.jpg", mine[prepared])
        assertEquals("/thumbnails/live/$ended.jpg", mine[ended])
        assertEquals(mapOf(live to "/thumbnails/live/$live.jpg"), thumbnailsOf("/api/broadcasts?status=LIVE", null))
        assertEquals(mapOf(ended to "/thumbnails/live/$ended.jpg"), thumbnailsOf("/api/broadcasts?status=ENDED", null))
    }

    /** 목록의 방송 ID → 썸네일 주소. */
    @Suppress("UNCHECKED_CAST")
    private fun thumbnailsOf(uri: String, cookie: String?): Map<Long, Any?> {
        val response = http.get().uri(uri).withCookie(cookie).retrieve().toEntity(Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return (response.body!!["items"] as List<Map<String, Any?>>).associate { (it["id"] as Number).toLong() to it["thumbnailUrl"] }
    }
}
