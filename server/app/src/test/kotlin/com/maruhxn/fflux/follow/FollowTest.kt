package com.maruhxn.fflux.follow

import com.maruhxn.fflux.broadcast.BroadcastFlowTest
import com.maruhxn.fflux.support.SseEvent
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 스트리머 팔로우와 방송 시작 알림(회원별 SSE `/api/me/events`). */
class FollowTest : BroadcastFlowTest() {

    private val streamerId get() = memberIdOf("streamer")

    private fun follow(streamerId: Long, cookie: String?): ResponseEntity<Void> =
        http.put().uri("/api/streamers/{id}/follow", streamerId).withCookie(cookie).retrieve().toBodilessEntity()

    private fun unfollow(streamerId: Long, cookie: String?): ResponseEntity<Void> =
        http.delete().uri("/api/streamers/{id}/follow", streamerId).withCookie(cookie).retrieve().toBodilessEntity()

    @Suppress("UNCHECKED_CAST")
    private fun status(streamerId: Long, cookie: String?): Map<String, Any?> =
        http.get().uri("/api/streamers/{id}/follow", streamerId).withCookie(cookie)
            .retrieve().body(Map::class.java) as Map<String, Any?>

    @Test
    fun `팔로우하면 팔로워 수가 늘고 팔로우 중으로 보인다`() {
        assertEquals(HttpStatus.NO_CONTENT, follow(streamerId, other).statusCode)

        assertEquals(mapOf("followerCount" to 1, "following" to true), status(streamerId, other))
        assertEquals(mapOf("followerCount" to 1, "following" to false), status(streamerId, null))
    }

    @Test
    fun `이미 팔로우했으면 다시 팔로우해도 한 번만 센다`() {
        follow(streamerId, other)

        assertEquals(HttpStatus.NO_CONTENT, follow(streamerId, other).statusCode)
        assertEquals(1, status(streamerId, other)["followerCount"])
    }

    @Test
    fun `언팔로우하면 팔로우 중이 아니고 팔로우 중이 아니어도 성공한다`() {
        follow(streamerId, other)

        assertEquals(HttpStatus.NO_CONTENT, unfollow(streamerId, other).statusCode)
        assertEquals(mapOf("followerCount" to 0, "following" to false), status(streamerId, other))
        assertEquals(HttpStatus.NO_CONTENT, unfollow(streamerId, other).statusCode)
    }

    @Test
    fun `팔로우와 언팔로우는 비로그인 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, follow(streamerId, null).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, unfollow(streamerId, null).statusCode)
    }

    @Test
    fun `자기 자신은 409 없는 회원은 404`() {
        assertEquals(HttpStatus.CONFLICT, follow(streamerId, streamer).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, follow(streamerId + 100, other).statusCode)
        assertEquals(0, status(streamerId, null)["followerCount"])
    }

    @Test
    fun `팔로우한 스트리머의 방송이 LIVE가 되면 방송 시작 알림을 받는다`() {
        follow(streamerId, other)
        val id = register(streamer)

        openSse("/api/me/events", other).use { events ->
            assertEquals(200, events.status)
            goLive(id)

            assertEquals(
                SseEvent("broadcast-started", """{"broadcastId":$id,"streamerNickname":"테스트스트리머","title":"방송"}"""),
                events.next(),
            )
        }
    }

    @Test
    fun `팔로우하지 않았거나 언팔로우한 스트리머의 방송 시작은 받지 않는다`() {
        follow(streamerId, other)
        unfollow(streamerId, other)
        val id = register(streamer)

        openSse("/api/me/events", other).use { events ->
            goLive(id)
            assertTrue(events.nothingWithin())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun followings(cookie: String?): ResponseEntity<List<Map<String, Any?>>> =
        http.get().uri("/api/me/followings").withCookie(cookie)
            .retrieve().toEntity(List::class.java) as ResponseEntity<List<Map<String, Any?>>>

    /** 테스트 계정 말고 스트리머 한 명을 더 가입시키고 로그인 쿠키와 회원 ID를 돌려준다. */
    private fun signUpStreamer(username: String, nickname: String): Pair<String, Long> {
        val body = mapOf("username" to username, "password" to "password1234", "nickname" to nickname)
        val signUp = http.post().uri("/api/members").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity()
        assertEquals(HttpStatus.CREATED, signUp.statusCode)
        val login = http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("username" to username, "password" to "password1234")).retrieve().toBodilessEntity()
        return sessionCookieOf(login.headers) to memberIdOf(username)
    }

    @Test
    fun `팔로우한 스트리머 목록은 방송 중인 스트리머가 먼저 오고 그 안에서는 최근에 팔로우한 순서다`() {
        val (_, quietId) = signUpStreamer("quiet01", "조용한스트리머")
        val (later, laterId) = signUpStreamer("later01", "나중스트리머")
        follow(streamerId, other)
        follow(quietId, other)
        follow(laterId, other)
        val liveId = goLive(register(later), later)

        val response = followings(other)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(
            listOf(
                mapOf("streamerId" to laterId.toInt(), "nickname" to "나중스트리머", "liveBroadcastId" to liveId.toInt()),
                mapOf("streamerId" to quietId.toInt(), "nickname" to "조용한스트리머", "liveBroadcastId" to null),
                mapOf("streamerId" to streamerId.toInt(), "nickname" to "테스트스트리머", "liveBroadcastId" to null),
            ),
            response.body,
        )
    }

    @Test
    fun `언팔로우한 스트리머는 목록에서 빠지고 비로그인은 401`() {
        follow(streamerId, other)
        unfollow(streamerId, other)

        assertEquals(emptyList(), followings(other).body)
        assertEquals(HttpStatus.UNAUTHORIZED, http.get().uri("/api/me/followings").retrieve().toBodilessEntity().statusCode)
    }

    @Test
    fun `회원별 SSE는 비로그인 401`() {
        openSse("/api/me/events").use { assertEquals(401, it.status) }
    }
}
