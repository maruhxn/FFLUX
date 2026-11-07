package com.maruhxn.fflux.realtime

import com.maruhxn.fflux.broadcast.BroadcastFlowTest
import com.maruhxn.fflux.support.SseEvent
import com.maruhxn.fflux.support.SseSubscription
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 방송별 SSE의 `viewer-count`: 로그인한 회원을 회원 단위로 세고, 비로그인과 스트리머는 세지 않는다. */
class ViewerCountTest : BroadcastFlowTest() {

    private fun subscribe(id: Long, cookie: String? = null): SseSubscription = openSse("/api/broadcasts/$id/events", cookie)

    private fun viewerCount(count: Int) = SseEvent("viewer-count", """{"count":$count}""")

    @Test
    fun `구독하면 지금 시청자 수를 바로 받는다`() {
        val id = goLive(register(streamer))

        subscribe(id, other).use { assertEquals(viewerCount(1), it.next()) }
    }

    @Test
    fun `같은 회원이 여러 화면으로 봐도 한 명이다`() {
        val id = goLive(register(streamer))

        subscribe(id, other).use { first ->
            assertEquals(viewerCount(1), first.next())
            subscribe(id, other).use { second -> assertEquals(viewerCount(1), second.next()) }
        }
    }

    @Test
    fun `비로그인 시청자와 스트리머는 세지 않는다`() {
        val id = goLive(register(streamer))

        subscribe(id).use { anonymous ->
            assertEquals(viewerCount(0), anonymous.next())
            subscribe(id, streamer).use { own -> assertEquals(viewerCount(0), own.next()) }
        }
    }

    @Test
    fun `시청자 수가 바뀌면 주기마다 모든 구독자에게 보내고, 그대로면 보내지 않는다`() {
        val id = goLive(register(streamer))

        subscribe(id).use { watcher ->
            assertEquals(viewerCount(0), watcher.next())
            subscribe(id, other).use { viewer ->
                assertEquals(viewerCount(1), viewer.next())

                hub.sendViewerCounts()
                assertEquals(viewerCount(1), watcher.next())

                hub.sendViewerCounts()
                assertTrue(watcher.nothingWithin())
            }
        }
    }

    @Test
    fun `회원의 마지막 연결이 끊기면 줄어든 수를 받는다`() {
        val id = goLive(register(streamer))

        subscribe(id).use { watcher ->
            assertEquals(viewerCount(0), watcher.next())
            subscribe(id, other).use { assertEquals(viewerCount(1), it.next()) }
            hub.sendViewerCounts()
            assertEquals(viewerCount(1), watcher.next())

            // 서버는 끊긴 연결을 쓰기에 실패해야 안다. heartbeat가 실패할 때까지 주기를 돌린다.
            assertEquals(viewerCount(0), nextAfterTicks(watcher))
        }
    }

    @Test
    fun `같은 회원의 연결이 하나라도 남아 있으면 그대로 한 명이다`() {
        val id = goLive(register(streamer))

        subscribe(id).use { watcher ->
            assertEquals(viewerCount(0), watcher.next())
            subscribe(id, other).use { remaining ->
                assertEquals(viewerCount(1), remaining.next())
                subscribe(id, other).use { assertEquals(viewerCount(1), it.next()) }
                hub.sendViewerCounts()
                assertEquals(viewerCount(1), watcher.next())

                // 닫은 연결이 빠질 만큼 주기를 돌려도 남은 연결이 있으니 바뀌지 않는다.
                repeat(5) { hub.heartbeat(); hub.sendViewerCounts() }
                assertTrue(watcher.nothingWithin())
            }
        }
    }

    @Test
    fun `LIVE 방송의 목록과 상세는 지금 시청자 수를 담고, 끝난 방송은 담지 않는다`() {
        val ended = goLive(register(streamer)).also { end(it, streamer) }
        val live = goLive(register(streamer))

        subscribe(live, other).use {
            assertEquals(viewerCount(1), it.next())

            assertEquals(1, itemOf(get("/api/broadcasts?status=LIVE"), live)["viewerCount"])
            assertEquals(1, get("/api/broadcasts/$live")["viewerCount"])
            assertNull(itemOf(get("/api/broadcasts?status=ENDED"), ended)["viewerCount"])
            assertNull(get("/api/broadcasts/$ended")["viewerCount"])
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun get(path: String): Map<String, Any?> =
        http.get().uri(path).retrieve().body(Map::class.java) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun itemOf(page: Map<String, Any?>, id: Long): Map<String, Any?> =
        (page["items"] as List<Map<String, Any?>>).single { (it["id"] as Number).toLong() == id }

    private fun nextAfterTicks(subscription: SseSubscription): SseEvent {
        repeat(20) {
            hub.heartbeat()
            hub.sendViewerCounts()
            subscription.nextOrNull(Duration.ofMillis(100))?.let { return it }
        }
        error("시청자 수 이벤트가 오지 않았습니다.")
    }
}
