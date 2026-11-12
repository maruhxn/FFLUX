package com.maruhxn.fflux.broadcast

import com.maruhxn.fflux.broadcast.application.provided.BroadcastStatusReconciler
import com.maruhxn.fflux.broadcast.application.required.MediaPublishing
import com.maruhxn.fflux.support.FakeMediaPublishingFinder
import com.maruhxn.fflux.support.SseEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 상태 맞추기: 놓친 MediaMTX 훅을 MediaMTX의 지금 송출 경로 목록으로 바로잡는다(docs/decisions.md 3절). */
class ReconcileTest : BroadcastFlowTest() {

    @Autowired
    private lateinit var reconciler: BroadcastStatusReconciler

    @Autowired
    private lateinit var mediaServer: FakeMediaPublishingFinder

    @BeforeEach
    fun resetMediaServer() {
        mediaServer.reset()
    }

    private fun publishing(path: String, availableAt: LocalDateTime = LocalDateTime.now(clock)) {
        mediaServer.publishings += MediaPublishing(path, availableAt)
    }

    private fun timeOf(id: Long, column: String): LocalDateTime =
        jdbcTemplate.queryForObject("SELECT $column FROM broadcast WHERE id = ?", LocalDateTime::class.java, id)!!

    @Test
    fun `송출이 없는 LIVE 방송은 송출 끊김으로 ENDED가 되고 발견한 시각이 종료 시각이다`() {
        val id = goLive(register(streamer))
        clock.advance(Duration.ofMinutes(1))
        val foundAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)

        reconciler.reconcile()

        assertEquals("ENDED", statusOf(id))
        assertEquals(foundAt, timeOf(id, "ended_at"))
        assertTrue(kicker.kickedPaths.isEmpty())
    }

    @Test
    fun `바로잡은 종료도 구독자에게 상태 변화를 보낸다`() {
        val id = goLive(register(streamer))

        openSse("/api/broadcasts/$id/events").use { events ->
            assertEquals("viewer-count", events.next().name)
            reconciler.reconcile()
            assertEquals(SseEvent("broadcast-status", """{"status":"ENDED"}"""), events.next())
        }
    }

    @Test
    fun `송출이 들어오는 READY 방송은 LIVE가 되고 미디어 서버가 송출을 받기 시작한 시각이 시작 시각이다`() {
        val id = register(streamer)
        auth("publish", "live/$id", tokenOf(issue(id, streamer)))
        val availableAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)
        publishing("live/$id", availableAt)
        clock.advance(Duration.ofMinutes(1))

        reconciler.reconcile()

        assertEquals("LIVE", statusOf(id))
        assertEquals(availableAt, timeOf(id, "started_at"))
    }

    @Test
    fun `끝난 방송에 송출이 남아 있으면 송출을 끊는다`() {
        val id = goLive(register(streamer))
        kicker.failing = true
        end(id, streamer)
        kicker.reset()
        publishing("live/$id")

        reconciler.reconcile()

        assertEquals("ENDED", statusOf(id))
        assertEquals(listOf("live/$id"), kicker.kickedPaths)
    }

    @Test
    fun `삭제된 방송에 송출이 남아 있으면 송출을 끊는다`() {
        val id = register(streamer)
        auth("publish", "live/$id", tokenOf(issue(id, streamer)))
        delete(id, streamer)
        publishing("live/$id")

        reconciler.reconcile()

        assertEquals(listOf("live/$id"), kicker.kickedPaths)
    }

    @Test
    fun `송출 중인 LIVE 방송은 그대로 두고 송출을 끊지 않는다`() {
        val id = goLive(register(streamer))
        publishing("live/$id")

        reconciler.reconcile()

        assertEquals("LIVE", statusOf(id))
        assertTrue(kicker.kickedPaths.isEmpty())
    }

    @Test
    fun `송출 끊기가 실패해도 남은 송출을 계속 끊는다`() {
        val first = goLive(register(streamer)).also { end(it, streamer) }
        val second = goLive(register(streamer)).also { end(it, streamer) }
        kicker.reset()
        kicker.failing = true
        publishing("live/$first")
        publishing("live/$second")

        reconciler.reconcile()

        assertEquals(listOf("live/$first", "live/$second"), kicker.kickedPaths)
    }

    @Test
    fun `미디어 서버 조회에 실패하면 아무 상태도 바꾸지 않는다`() {
        val id = goLive(register(streamer))
        mediaServer.failing = true

        reconciler.reconcile()

        assertEquals("LIVE", statusOf(id))
    }
}
