package com.maruhxn.fflux.broadcast

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 방송 종료: 스트리머의 종료 요청(`/api/broadcasts/{id}/end`)과 송출 끊김(`unavailable` 훅). */
class EndTest : BroadcastFlowTest() {

    // 종료 요청

    @Test
    fun `스트리머가 종료를 요청하면 LIVE 방송이 ENDED가 되고 송출 세션을 끊는다`() {
        val id = goLive(register(streamer))

        assertEquals(HttpStatus.NO_CONTENT, end(id, streamer).statusCode)

        assertEquals("ENDED", statusOf(id))
        assertEquals(listOf("live/$id"), kicker.kickedPaths)
    }

    @Test
    fun `종료 요청은 비로그인 401 타인 403 없는 방송 404`() {
        val id = goLive(register(streamer))

        assertEquals(HttpStatus.UNAUTHORIZED, end(id, null).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, end(id, other).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, end(id + 100, streamer).statusCode)
        assertEquals("LIVE", statusOf(id))
        assertTrue(kicker.kickedPaths.isEmpty())
    }

    @Test
    fun `삭제된 방송의 종료 요청은 404`() {
        val id = register(streamer)
        delete(id, streamer)

        assertEquals(HttpStatus.NOT_FOUND, end(id, streamer).statusCode)
    }

    @Test
    fun `READY 방송의 종료 요청은 409`() {
        val id = register(streamer)

        assertEquals(HttpStatus.CONFLICT, end(id, streamer).statusCode)
        assertEquals("READY", statusOf(id))
    }

    @Test
    fun `타인이 READY 방송의 종료를 요청하면 409가 아니라 403`() {
        val id = register(streamer)

        assertEquals(HttpStatus.FORBIDDEN, end(id, other).statusCode)
    }

    @Test
    fun `이미 ENDED인 방송의 종료 요청은 성공하고 다시 kick하지 않는다`() {
        val id = goLive(register(streamer))
        end(id, streamer)

        assertEquals(HttpStatus.NO_CONTENT, end(id, streamer).statusCode)
        assertEquals(1, kicker.kickedPaths.size)
    }

    @Test
    fun `kick이 실패해도 종료 요청은 성공하고 방송은 ENDED다`() {
        val id = goLive(register(streamer))
        kicker.failing = true

        assertEquals(HttpStatus.NO_CONTENT, end(id, streamer).statusCode)
        assertEquals("ENDED", statusOf(id))
    }

    // 송출 끊김

    @Test
    fun `송출이 끊기면 LIVE 방송이 바로 ENDED가 되고 kick하지 않는다`() {
        val id = goLive(register(streamer))

        assertEquals(HttpStatus.OK, unavailable("live/$id").statusCode)

        assertEquals("ENDED", statusOf(id))
        assertTrue(kicker.kickedPaths.isEmpty())
    }

    @Test
    fun `송출 끊김 훅은 전이할 수 없어도 200이고 상태를 바꾸지 않는다`() {
        val ready = register(streamer).also { issue(it, streamer) }

        assertEquals(HttpStatus.OK, unavailable("live/$ready").statusCode)
        assertEquals("READY", statusOf(ready))
        assertEquals(HttpStatus.OK, unavailable("live/9999").statusCode)
    }

    @Test
    fun `송출 끊김 뒤의 종료 요청과 종료 요청 뒤의 송출 끊김은 모두 성공한다`() {
        val disconnectedFirst = goLive(register(streamer))
        assertEquals(HttpStatus.OK, unavailable("live/$disconnectedFirst").statusCode)
        assertEquals(HttpStatus.NO_CONTENT, end(disconnectedFirst, streamer).statusCode)
        assertEquals("ENDED", statusOf(disconnectedFirst))

        val endedFirst = goLive(register(streamer))
        assertEquals(HttpStatus.NO_CONTENT, end(endedFirst, streamer).statusCode)
        assertEquals(HttpStatus.OK, unavailable("live/$endedFirst").statusCode)
        assertEquals("ENDED", statusOf(endedFirst))

        // 송출 끊김이 먼저 끝낸 방송은 kick하지 않는다.
        assertEquals(listOf("live/$endedFirst"), kicker.kickedPaths)
    }

    @Test
    fun `종료 요청과 송출 끊김이 동시에 와도 둘 다 성공하고 kick은 많아야 한 번이다`() {
        repeat(5) {
            kicker.reset()
            val id = goLive(register(streamer))
            val start = CountDownLatch(1)

            val endCall = CompletableFuture.supplyAsync { start.await(); end(id, streamer).statusCode }
            val hookCall = CompletableFuture.supplyAsync { start.await(); unavailable("live/$id").statusCode }
            start.countDown()

            assertEquals(HttpStatus.NO_CONTENT, endCall.get())
            assertEquals(HttpStatus.OK, hookCall.get())
            assertEquals("ENDED", statusOf(id))
            assertTrue(kicker.kickedPaths.size <= 1)
        }
    }

    @Test
    fun `종료된 방송은 다시 송출할 수 없다`() {
        val id = goLive(register(streamer))
        end(id, streamer)

        assertEquals(HttpStatus.CONFLICT, issue(id, streamer).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, auth("read", "live/$id", null).statusCode)
    }

    @Test
    fun `방송을 종료하면 같은 스트리머가 새 방송을 송출할 수 있다`() {
        val first = goLive(register(streamer))
        val next = register(streamer)
        assertEquals(HttpStatus.CONFLICT, issue(next, streamer).statusCode)

        end(first, streamer)

        assertEquals(HttpStatus.OK, issue(next, streamer).statusCode)
    }
}
