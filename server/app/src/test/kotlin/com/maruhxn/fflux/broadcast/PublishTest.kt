package com.maruhxn.fflux.broadcast

import com.maruhxn.fflux.auth.TestAccountSeeder
import com.maruhxn.fflux.broadcast.domain.PublishToken
import com.maruhxn.fflux.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 송출 토큰 발급(`/api`)과 MediaMTX 인증 콜백·`available` 훅(`/internal`)으로 방송이 LIVE가 되는 흐름.
 * ENDED로 가는 경로(#8)가 아직 없어서 준비 단계에서만 ENDED를 DB에 직접 쓴다.
 */
class PublishTest : IntegrationTest() {

    @Autowired
    private lateinit var testAccountSeeder: TestAccountSeeder

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var streamer: String
    private lateinit var other: String

    @BeforeEach
    fun logIn() {
        testAccountSeeder.run(DefaultApplicationArguments())
        streamer = testLogin("streamer")
        other = testLogin("viewer")
    }

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

    /** 실제 흐름(토큰 발급 → publish 인증 → available 훅)으로 방송을 LIVE로 만든다. */
    private fun goLive(id: Long, cookie: String = streamer): Long {
        val token = tokenOf(issue(id, cookie, record = false))
        assertEquals(HttpStatus.OK, auth("publish", "live/$id", token).statusCode)
        available("live/$id")
        assertEquals("LIVE", statusOf(id))
        return id
    }

    @Suppress("UNCHECKED_CAST")
    private fun issue(id: Long, cookie: String?, record: Boolean = false): ResponseEntity<Map<String, Any?>> =
        http.post().uri("/api/broadcasts/{id}/publish-token", id).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("record" to record))
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    private fun tokenOf(response: ResponseEntity<Map<String, Any?>>): String {
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!["token"] as String
    }

    /** MediaMTX가 보내는 인증 요청 본문 형태 그대로 보낸다. */
    private fun auth(action: String, path: String, token: String?): ResponseEntity<Void> =
        http.post().uri("/internal/mediamtx/auth")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "user" to "", "password" to "", "token" to (token ?: ""), "ip" to "172.18.0.1",
                    "action" to action, "path" to path, "protocol" to "webrtc", "id" to null, "query" to "",
                ),
            )
            .retrieve().toBodilessEntity()

    private fun available(path: String): ResponseEntity<Void> =
        http.post().uri("/internal/mediamtx/available")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("path" to path))
            .retrieve().toBodilessEntity()

    private fun statusOf(id: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM broadcast WHERE id = ?", String::class.java, id)!!

    private fun setStatus(id: Long, status: String) {
        jdbcTemplate.update("UPDATE broadcast SET status = ? WHERE id = ?", status, id)
    }

    private fun delete(id: Long, cookie: String?): ResponseEntity<Void> =
        http.delete().uri("/api/broadcasts/{id}", id).withCookie(cookie).retrieve().toBodilessEntity()

    private fun register(cookie: String): Long {
        val response = http.post().uri("/api/broadcasts").withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("title" to "방송"))
            .retrieve().toEntity(Map::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return (response.body!!["id"] as Number).toLong()
    }

    private fun testLogin(account: String): String =
        sessionCookieOf(
            http.post().uri("/api/auth/test-login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("account" to account))
                .retrieve().toBodilessEntity().headers,
        )

    private fun <S : RestClient.RequestHeadersSpec<S>> S.withCookie(cookie: String?): S =
        apply { if (cookie != null) header(HttpHeaders.COOKIE, cookie) }
}
