package com.maruhxn.fflux.broadcast

import com.maruhxn.fflux.broadcast.domain.PublishToken
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 송출 토큰 발급(`/api`)과 MediaMTX 인증 콜백·`available` 훅(`/internal`)으로 방송이 LIVE가 되는 흐름.
 * ENDED가 필요한 준비 단계에서는 READY 방송을 바로 ENDED로 만들려고 DB에 상태를 직접 쓴다(실제 흐름으로는 LIVE를 거쳐야 한다).
 */
class PublishTest : BroadcastFlowTest() {

    // 송출 토큰 발급

    @Test
    fun `스트리머가 녹화를 고르면 rec 경로 고르지 않으면 live 경로의 WHIP URL과 토큰을 받는다`() {
        val recorded = register(streamer)
        val unrecorded = register(streamer)

        val recordedBody = issue(recorded, streamer, record = true).body!!
        val unrecordedBody = issue(unrecorded, streamer, record = false).body!!

        assertEquals("/media/rec/$recorded/whip", recordedBody["whipUrl"])
        assertEquals("/media/live/$unrecorded/whip", unrecordedBody["whipUrl"])
        assertTrue((recordedBody["token"] as String).length >= 32)
    }

    @Test
    fun `DB에는 송출 토큰 원본이 아니라 해시만 남는다`() {
        val id = register(streamer)

        val token = tokenOf(issue(id, streamer))

        val stored = jdbcTemplate.queryForList("SELECT token_hash FROM publish_token", String::class.java)
        assertEquals(listOf(PublishToken.hash(token)), stored)
    }

    @Test
    fun `송출 토큰은 비로그인 401 타인 403 없는 방송 404`() {
        val id = register(streamer)

        assertEquals(HttpStatus.UNAUTHORIZED, issue(id, null).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, issue(id, other).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, issue(id + 100, streamer).statusCode)
    }

    @Test
    fun `LIVE나 ENDED 방송의 송출 토큰 발급은 409`() {
        val live = goLive(register(streamer))
        val ended = register(streamer).also { setStatus(it, "ENDED") }

        assertEquals(HttpStatus.CONFLICT, issue(live, streamer).statusCode)
        assertEquals(HttpStatus.CONFLICT, issue(ended, streamer).statusCode)
    }

    @Test
    fun `타인이 LIVE 방송의 송출 토큰을 받으려 하면 409가 아니라 403`() {
        val live = goLive(register(streamer))

        assertEquals(HttpStatus.FORBIDDEN, issue(live, other).statusCode)
    }

    @Test
    fun `같은 스트리머의 다른 방송이 LIVE면 발급은 409`() {
        goLive(register(streamer))
        val next = register(streamer)

        assertEquals(HttpStatus.CONFLICT, issue(next, streamer).statusCode)
    }

    @Test
    fun `다른 스트리머의 LIVE 방송은 발급을 막지 않는다`() {
        goLive(register(other), other)
        val id = register(streamer)

        assertEquals(HttpStatus.OK, issue(id, streamer).statusCode)
    }

    @Test
    fun `송출 토큰을 한 번 쓴 방송은 LIVE가 되기 전이라도 다시 발급받을 수 없다`() {
        val id = register(streamer)
        val token = tokenOf(issue(id, streamer, record = true))
        assertEquals(HttpStatus.OK, auth("publish", "rec/$id", token).statusCode)

        assertEquals(HttpStatus.CONFLICT, issue(id, streamer, record = false).statusCode)

        available("rec/$id")
        assertEquals("LIVE", statusOf(id))
    }

    // publish 인증

    @Test
    fun `유효한 토큰의 publish는 허용하고 같은 토큰은 다시 쓸 수 없다`() {
        val id = register(streamer)
        val token = tokenOf(issue(id, streamer, record = true))

        assertEquals(HttpStatus.OK, auth("publish", "rec/$id", token).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, auth("publish", "rec/$id", token).statusCode)
    }

    @Test
    fun `만료된 토큰의 publish는 거절한다`() {
        val id = register(streamer)
        val token = tokenOf(issue(id, streamer))

        clock.advance(PublishToken.TTL.plus(Duration.ofSeconds(1)))

        assertEquals(HttpStatus.UNAUTHORIZED, auth("publish", "live/$id", token).statusCode)
    }

    @Test
    fun `토큰에 바인딩된 경로와 다른 path의 publish는 거절한다`() {
        val id = register(streamer)
        val otherBroadcast = register(streamer).also { issue(it, streamer, record = false) }
        val token = tokenOf(issue(id, streamer, record = true))

        listOf("live/$id", "live/$otherBroadcast", "rec/$otherBroadcast").forEach { path ->
            assertEquals(HttpStatus.UNAUTHORIZED, auth("publish", path, token).statusCode, path)
        }
        assertEquals(HttpStatus.OK, auth("publish", "rec/$id", token).statusCode)
    }

    @Test
    fun `다시 발급하면 이전 토큰은 거절하고 새 토큰과 새 녹화 선택을 쓴다`() {
        val id = register(streamer)
        val old = tokenOf(issue(id, streamer, record = true))
        val new = tokenOf(issue(id, streamer, record = false))

        assertEquals(HttpStatus.UNAUTHORIZED, auth("publish", "rec/$id", old).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, auth("publish", "live/$id", old).statusCode)
        assertEquals(HttpStatus.OK, auth("publish", "live/$id", new).statusCode)
    }

    @Test
    fun `삭제한 방송의 토큰은 거절한다`() {
        val id = register(streamer)
        val token = tokenOf(issue(id, streamer))

        assertEquals(HttpStatus.NO_CONTENT, delete(id, streamer).statusCode)

        assertEquals(HttpStatus.UNAUTHORIZED, auth("publish", "live/$id", token).statusCode)
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM publish_token WHERE revoked_at IS NOT NULL", Int::class.java))
    }

    @Test
    fun `같은 스트리머의 다른 방송이 LIVE가 되면 먼저 받아 둔 토큰의 publish는 거절한다`() {
        val first = register(streamer)
        val second = register(streamer)
        val firstToken = tokenOf(issue(first, streamer))
        val secondToken = tokenOf(issue(second, streamer))

        assertEquals(HttpStatus.OK, auth("publish", "live/$first", firstToken).statusCode)
        available("live/$first")

        assertEquals(HttpStatus.UNAUTHORIZED, auth("publish", "live/$second", secondToken).statusCode)
    }

    @Test
    fun `토큰이 없거나 모르는 토큰의 publish는 거절한다`() {
        val id = register(streamer)
        issue(id, streamer)

        assertEquals(HttpStatus.UNAUTHORIZED, auth("publish", "live/$id", null).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, auth("publish", "live/$id", "unknown-token").statusCode)
    }

    // read 인증과 그 밖의 action

    @Test
    fun `read는 토큰 없이 LIVE 방송만 허용한다`() {
        val id = register(streamer)
        val token = tokenOf(issue(id, streamer))

        assertEquals(HttpStatus.UNAUTHORIZED, auth("read", "live/$id", null).statusCode)
        auth("publish", "live/$id", token)
        available("live/$id")
        assertEquals(HttpStatus.OK, auth("read", "live/$id", null).statusCode)

        setStatus(id, "ENDED")
        assertEquals(HttpStatus.UNAUTHORIZED, auth("read", "live/$id", null).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, auth("read", "live/9999", null).statusCode)
    }

    @Test
    fun `publish와 read가 아닌 action은 거절한다`() {
        val id = goLive(register(streamer))

        assertEquals(HttpStatus.UNAUTHORIZED, auth("playback", "live/$id", null).statusCode)
    }

    // available 훅

    @Test
    fun `available 훅이 오면 방송이 LIVE가 되고 다시 와도 LIVE 그대로다`() {
        val id = register(streamer)
        auth("publish", "live/$id", tokenOf(issue(id, streamer)))

        assertEquals(HttpStatus.OK, available("live/$id").statusCode)
        assertEquals("LIVE", statusOf(id))
        assertEquals(HttpStatus.OK, available("live/$id").statusCode)
        assertEquals("LIVE", statusOf(id))
    }

    @Test
    fun `available 훅은 전이할 수 없어도 200이고 상태를 바꾸지 않는다`() {
        val ended = register(streamer)
        issue(ended, streamer)
        setStatus(ended, "ENDED")
        val deleted = register(streamer)
        issue(deleted, streamer)
        delete(deleted, streamer)

        assertEquals(HttpStatus.OK, available("live/$ended").statusCode)
        assertEquals("ENDED", statusOf(ended))
        assertEquals(HttpStatus.OK, available("live/$deleted").statusCode)
        assertEquals("READY", statusOf(deleted))
        assertEquals(HttpStatus.OK, available("live/9999").statusCode)
    }
}
