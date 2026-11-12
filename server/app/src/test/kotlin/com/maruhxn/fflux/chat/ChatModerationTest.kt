package com.maruhxn.fflux.chat

import com.maruhxn.fflux.broadcast.BroadcastFlowTest
import com.maruhxn.fflux.support.SseEvent
import com.maruhxn.fflux.support.SseSubscription
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 채팅 관리: 스트리머의 채팅 가리기와 채팅 금지(docs/decisions.md 6절). */
class ChatModerationTest : BroadcastFlowTest() {

    private val json = JsonMapper.builder().build()

    // 채팅 가리기

    @Test
    fun `스트리머가 가린 채팅은 목록에 자리를 남기고 본문과 닉네임을 가린다`() {
        val id = goLive(register(streamer))
        val hiddenId = postChat(id, other, "욕설")
        postChat(id, other, "안녕하세요")

        assertEquals(HttpStatus.NO_CONTENT, hide(hiddenId, streamer).statusCode)

        val chats = chats(id)
        assertEquals(listOf(true, false), chats.map { it["hidden"] })
        assertEquals(listOf(null, "안녕하세요"), chats.map { it["content"] })
        assertEquals(listOf(null, "테스트시청자"), chats.map { it["authorNickname"] })
    }

    @Test
    fun `스트리머는 자기 채팅도 가리고 이미 가린 채팅을 다시 가려도 성공한다`() {
        val id = goLive(register(streamer))
        val chatId = postChat(id, streamer, "실수")

        assertEquals(HttpStatus.NO_CONTENT, hide(chatId, streamer).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, hide(chatId, streamer).statusCode)
        assertEquals(listOf(true), chats(id).map { it["hidden"] })
    }

    @Test
    fun `채팅 가리기는 비로그인 401 타인 403 없는 채팅 404`() {
        val id = goLive(register(streamer))
        val chatId = postChat(id, other, "안녕")

        assertEquals(HttpStatus.UNAUTHORIZED, hide(chatId, null).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, hide(chatId, other).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, hide(chatId + 100, streamer).statusCode)
        assertEquals(listOf(false), chats(id).map { it["hidden"] })
    }

    @Test
    fun `삭제된 방송의 채팅 가리기는 404`() {
        val id = goLive(register(streamer))
        val chatId = postChat(id, other, "안녕")
        end(id, streamer)
        delete(id, streamer)

        assertEquals(HttpStatus.NOT_FOUND, hide(chatId, streamer).statusCode)
    }

    @Test
    fun `채팅을 가리면 구독자에게 가린 채팅 ID를 보낸다`() {
        val id = goLive(register(streamer))
        val chatId = postChat(id, other, "욕설")

        subscribe(id).use { events ->
            hide(chatId, streamer)

            assertEquals(SseEvent("chat-hidden", """{"id":$chatId}"""), events.next())
            hide(chatId, streamer)
            assertTrue(events.nothingWithin())
        }
    }

    // 채팅 금지

    @Test
    fun `금지된 회원은 그 스트리머의 방송에 채팅할 수 없고 다른 스트리머의 방송에는 채팅할 수 있다`() {
        val id = goLive(register(streamer))
        val chatId = postChat(id, other, "도배")
        val otherStreamer = signUpAndLogIn("streamer2")
        val otherBroadcast = goLive(register(otherStreamer), otherStreamer)

        assertEquals(HttpStatus.NO_CONTENT, restrict(chatId, streamer, "UNTIL_LIFTED").statusCode)

        assertEquals(HttpStatus.FORBIDDEN, postChatResponse(id, other, "다시 도배").statusCode)
        assertEquals(HttpStatus.CREATED, postChatResponse(otherBroadcast, other, "안녕").statusCode)
    }

    @Test
    fun `금지는 그 스트리머의 다음 방송에도 걸리고 LIVE가 아닌 방송이어도 409보다 403이 먼저다`() {
        val first = goLive(register(streamer))
        restrict(postChat(first, other, "도배"), streamer, "UNTIL_LIFTED")
        end(first, streamer)

        val next = register(streamer)
        assertEquals(HttpStatus.FORBIDDEN, postChatResponse(next, other, "또 왔다").statusCode)
        goLive(next)
        assertEquals(HttpStatus.FORBIDDEN, postChatResponse(next, other, "또 왔다").statusCode)
    }

    @Test
    fun `30초 금지와 1시간 금지는 기간이 지나면 저절로 풀린다`() {
        val id = goLive(register(streamer))

        restrict(postChat(id, other, "도배"), streamer, "THIRTY_SECONDS")
        clock.advance(Duration.ofSeconds(29))
        assertEquals(HttpStatus.FORBIDDEN, postChatResponse(id, other, "아직").statusCode)
        clock.advance(Duration.ofSeconds(1))
        val again = postChat(id, other, "풀렸다")

        restrict(again, streamer, "ONE_HOUR")
        clock.advance(Duration.ofMinutes(59))
        assertEquals(HttpStatus.FORBIDDEN, postChatResponse(id, other, "아직").statusCode)
        clock.advance(Duration.ofMinutes(1))
        assertEquals(HttpStatus.CREATED, postChatResponse(id, other, "풀렸다").statusCode)
    }

    @Test
    fun `풀 때까지 금지는 하루가 지나도 이어지고 다시 금지하면 마지막 선택으로 바뀐다`() {
        val id = goLive(register(streamer))
        val chatId = postChat(id, other, "도배")

        restrict(chatId, streamer, "UNTIL_LIFTED")
        clock.advance(Duration.ofDays(1))
        assertEquals(HttpStatus.FORBIDDEN, postChatResponse(id, other, "아직").statusCode)

        assertEquals(HttpStatus.NO_CONTENT, restrict(chatId, streamer, "THIRTY_SECONDS").statusCode)
        clock.advance(Duration.ofSeconds(30))
        assertEquals(HttpStatus.CREATED, postChatResponse(id, other, "풀렸다").statusCode)
    }

    @Test
    fun `채팅 금지는 비로그인 401 타인 403 없는 채팅 404 스트리머 자신 409`() {
        val id = goLive(register(streamer))
        val chatId = postChat(id, other, "안녕")
        val streamerChatId = postChat(id, streamer, "어서 오세요")

        assertEquals(HttpStatus.UNAUTHORIZED, restrict(chatId, null, "THIRTY_SECONDS").statusCode)
        assertEquals(HttpStatus.FORBIDDEN, restrict(streamerChatId, other, "THIRTY_SECONDS").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, restrict(chatId + 100, streamer, "THIRTY_SECONDS").statusCode)
        assertEquals(HttpStatus.CONFLICT, restrict(streamerChatId, streamer, "THIRTY_SECONDS").statusCode)
        assertEquals(HttpStatus.CREATED, postChatResponse(id, other, "아직 채팅할 수 있다").statusCode)
    }

    // 해제, 금지 목록, 내 금지 상태

    @Test
    fun `스트리머가 금지를 풀면 다시 채팅할 수 있고 금지 중이 아니어도 해제는 성공한다`() {
        val id = goLive(register(streamer))
        restrict(postChat(id, other, "도배"), streamer, "UNTIL_LIFTED")

        assertEquals(HttpStatus.NO_CONTENT, lift(memberIdOf("viewer"), streamer).statusCode)
        assertEquals(HttpStatus.CREATED, postChatResponse(id, other, "다시 왔다").statusCode)
        assertEquals(HttpStatus.NO_CONTENT, lift(memberIdOf("viewer"), streamer).statusCode)
    }

    @Test
    fun `다른 회원의 해제는 자기 금지만 다뤄 남의 금지를 풀지 못한다`() {
        val id = goLive(register(streamer))
        restrict(postChat(id, other, "도배"), streamer, "UNTIL_LIFTED")
        val otherStreamer = signUpAndLogIn("streamer2")

        assertEquals(HttpStatus.UNAUTHORIZED, lift(memberIdOf("viewer"), null).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, lift(memberIdOf("viewer"), otherStreamer).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, postChatResponse(id, other, "아직").statusCode)
    }

    @Test
    fun `금지 목록은 로그인한 스트리머 자신의 지금 금지 중인 회원만 금지 당시 닉네임과 함께 보여 준다`() {
        val id = goLive(register(streamer))
        val now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)
        restrict(postChat(id, other, "도배"), streamer, "THIRTY_SECONDS")

        val restrictions = restrictions(streamer)
        assertEquals(1, restrictions.size)
        assertEquals(memberIdOf("viewer"), (restrictions[0]["memberId"] as Number).toLong())
        assertEquals("테스트시청자", restrictions[0]["memberNickname"])
        assertEquals(now.plusSeconds(30), LocalDateTime.parse(restrictions[0]["until"] as String))
        assertEquals(emptyList(), restrictions(other))
        assertEquals(HttpStatus.UNAUTHORIZED, http.get().uri("/api/chat-restrictions").retrieve().toBodilessEntity().statusCode)

        clock.advance(Duration.ofSeconds(30))
        assertEquals(emptyList(), restrictions(streamer))
    }

    @Test
    fun `채팅 목록은 로그인 회원 자신의 금지 상태를 함께 준다`() {
        val id = goLive(register(streamer))
        val chatId = postChat(id, other, "도배")
        assertNull(chatsBody(id, other)["restriction"])

        restrict(chatId, streamer, "UNTIL_LIFTED")
        assertEquals(mapOf("until" to null), chatsBody(id, other)["restriction"])
        assertNull(chatsBody(id, null)["restriction"])
        assertNull(chatsBody(id, streamer)["restriction"])

        restrict(chatId, streamer, "ONE_HOUR")
        val until = LocalDateTime.parse((chatsBody(id, other)["restriction"] as Map<*, *>)["until"] as String)
        assertEquals(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS).plusHours(1), until)
    }

    @Test
    fun `금지와 해제는 스트리머의 LIVE 방송 구독자에게 회원 ID와 언제까지를 보낸다`() {
        val id = goLive(register(streamer))
        val chatId = postChat(id, other, "도배")
        val viewerId = memberIdOf("viewer")

        subscribe(id).use { events ->
            restrict(chatId, streamer, "THIRTY_SECONDS")
            val restricted = events.next()
            assertEquals("chat-restriction", restricted.name)
            val body = json.readValue(restricted.data, Map::class.java)
            assertEquals(viewerId, (body["memberId"] as Number).toLong())
            assertEquals(true, body["restricted"])
            assertEquals(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS).plusSeconds(30), LocalDateTime.parse(body["until"] as String))

            lift(viewerId, streamer)
            assertEquals(SseEvent("chat-restriction", """{"memberId":$viewerId,"restricted":false,"until":null}"""), events.next())
        }
    }

    /** 구독 직후 오는 시청자 수 이벤트는 넘긴다. */
    private fun subscribe(id: Long, cookie: String? = null): SseSubscription =
        openSse("/api/broadcasts/$id/events", cookie).also { assertEquals("viewer-count", it.next().name) }

    private fun postChat(id: Long, cookie: String, content: String): Long {
        val response = postChatResponse(id, cookie, content)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return (response.body!!["id"] as Number).toLong()
    }

    private fun postChatResponse(id: Long, cookie: String, content: String): ResponseEntity<Map<*, *>> =
        http.post().uri("/api/broadcasts/{id}/chats", id).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("content" to content))
            .retrieve().toEntity(Map::class.java)

    private fun restrict(chatId: Long, cookie: String?, duration: String): ResponseEntity<Void> =
        http.post().uri("/api/chats/{id}/restrict", chatId).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("duration" to duration))
            .retrieve().toBodilessEntity()

    private fun lift(memberId: Long, cookie: String?): ResponseEntity<Void> =
        http.delete().uri("/api/chat-restrictions/{memberId}", memberId).withCookie(cookie).retrieve().toBodilessEntity()

    @Suppress("UNCHECKED_CAST")
    private fun restrictions(cookie: String): List<Map<String, Any?>> {
        val response = http.get().uri("/api/chat-restrictions").withCookie(cookie).retrieve().toEntity(List::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body as List<Map<String, Any?>>
    }

    /** 시드 계정 말고 다른 스트리머가 필요할 때 가입하고 로그인한다. */
    private fun signUpAndLogIn(username: String): String {
        val password = "password123"
        val signUp = http.post().uri("/api/members").contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("username" to username, "password" to password, "nickname" to "둘째스트리머"))
            .retrieve().toBodilessEntity()
        assertEquals(HttpStatus.CREATED, signUp.statusCode)
        return sessionCookieOf(
            http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("username" to username, "password" to password))
                .retrieve().toBodilessEntity().headers,
        )
    }

    private fun hide(chatId: Long, cookie: String?): ResponseEntity<Void> =
        http.post().uri("/api/chats/{id}/hide", chatId).withCookie(cookie).retrieve().toBodilessEntity()

    @Suppress("UNCHECKED_CAST")
    private fun chatsBody(id: Long, cookie: String? = null): Map<String, Any?> {
        val response = http.get().uri("/api/broadcasts/{id}/chats", id).withCookie(cookie).retrieve().toEntity(Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun chats(id: Long, cookie: String? = null): List<Map<String, Any?>> = chatsBody(id, cookie)["chats"] as List<Map<String, Any?>>
}
