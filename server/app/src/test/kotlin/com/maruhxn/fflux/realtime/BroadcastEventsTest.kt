package com.maruhxn.fflux.realtime

import com.maruhxn.fflux.broadcast.BroadcastFlowTest
import com.maruhxn.fflux.support.SseEvent
import com.maruhxn.fflux.support.SseSubscription
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 방송별 SSE(`/api/broadcasts/{id}/events`): 상태 변화와 정보 수정이 구독자에게 오는지. */
class BroadcastEventsTest : BroadcastFlowTest() {

    /** 구독 직후 오는 시청자 수 이벤트는 넘기고, 그 뒤의 이벤트를 본다. */
    private fun subscribe(id: Long, cookie: String? = null): SseSubscription =
        openSse("/api/broadcasts/$id/events", cookie).also { if (it.status == 200) assertEquals("viewer-count", it.next().name) }

    private fun edit(id: Long, title: String, description: String?) {
        val response = http.patch().uri("/api/broadcasts/{id}", id).withCookie(streamer)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("title" to title, "description" to description))
            .retrieve().toBodilessEntity()
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    private fun statusEvent(status: String) = SseEvent("broadcast-status", """{"status":"$status"}""")

    @Test
    fun `비로그인도 구독할 수 있고 송출이 시작되면 LIVE를 받는다`() {
        val id = register(streamer)
        val token = tokenOf(issue(id, streamer))
        auth("publish", "live/$id", token)

        subscribe(id).use { events ->
            assertEquals(200, events.status)
            available("live/$id")
            assertEquals(statusEvent("LIVE"), events.next())
        }
    }

    @Test
    fun `방송을 수정하면 바뀐 제목과 설명을 받는다`() {
        val id = register(streamer)

        subscribe(id).use { events ->
            edit(id, "새 제목", "새 설명")
            assertEquals(SseEvent("broadcast-updated", """{"title":"새 제목","description":"새 설명"}"""), events.next())
        }
    }

    @Test
    fun `종료 요청으로 끝나면 ENDED를 받는다`() {
        val id = goLive(register(streamer))

        subscribe(id).use { events ->
            end(id, streamer)
            assertEquals(statusEvent("ENDED"), events.next())
        }
    }

    @Test
    fun `송출이 끊겨 끝나면 ENDED를 받는다`() {
        val id = goLive(register(streamer))

        subscribe(id).use { events ->
            unavailable("live/$id")
            assertEquals(statusEvent("ENDED"), events.next())
        }
    }

    @Test
    fun `종료 요청과 송출 끊김이 동시에 와도 ENDED는 한 번만 받는다`() {
        val id = goLive(register(streamer))

        subscribe(id).use { events ->
            val start = CountDownLatch(1)
            val endCall = CompletableFuture.runAsync { start.await(); end(id, streamer) }
            val hookCall = CompletableFuture.runAsync { start.await(); unavailable("live/$id") }
            start.countDown()
            CompletableFuture.allOf(endCall, hookCall).get()

            assertEquals(statusEvent("ENDED"), events.next())
            assertTrue(events.nothingWithin())
        }
    }

    @Test
    fun `다른 방송의 이벤트는 받지 않는다`() {
        val watched = register(streamer)
        val elsewhere = register(streamer)

        subscribe(watched).use { events ->
            edit(elsewhere, "다른 방송", null)
            edit(watched, "보는 방송", null)

            assertEquals(SseEvent("broadcast-updated", """{"title":"보는 방송","description":null}"""), events.next())
        }
    }

    @Test
    fun `없거나 삭제된 방송은 구독할 수 없다`() {
        val deleted = register(streamer)
        delete(deleted, streamer)

        subscribe(deleted + 100).use { assertEquals(404, it.status) }
        subscribe(deleted).use { assertEquals(404, it.status) }
    }
}
