package com.maruhxn.fflux.chat

import com.maruhxn.fflux.broadcast.BroadcastFlowTest
import com.maruhxn.fflux.support.SseEvent
import com.maruhxn.fflux.support.SseSubscription
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** LIVE 방송 채팅: 작성 조건(LIVE, 로그인), 입장 시 최근 50개, 작성하면 SSE `chat`. */
class ChatTest : BroadcastFlowTest() {

    @Test
    fun `로그인 회원과 스트리머가 LIVE 방송에 보낸 채팅은 작성 시점 닉네임과 함께 목록에 보인다`() {
        val id = goLive(register(streamer))

        assertEquals(HttpStatus.CREATED, post(id, other, "  안녕하세요  ").statusCode)
        assertEquals(HttpStatus.CREATED, post(id, streamer, "어서 오세요").statusCode)

        val chats = chats(id, cookie = null)
        // 로그인한 회원과 스트리머도 같은 목록을 본다.
        assertEquals(chats, chats(id, cookie = other))
        assertEquals(chats, chats(id, cookie = streamer))
        assertEquals(listOf("안녕하세요", "어서 오세요"), chats.map { it["content"] })
        assertEquals(listOf("테스트시청자", "테스트스트리머"), chats.map { it["authorNickname"] })
    }

    @Test
    fun `비로그인은 목록을 볼 수 있고 작성은 401`() {
        val id = goLive(register(streamer))

        assertEquals(HttpStatus.UNAUTHORIZED, post(id, null, "안녕").statusCode)
        assertEquals(emptyList(), chats(id, cookie = null))
    }

    @Test
    fun `빈 본문과 200자를 넘는 본문은 content 칸의 400`() {
        val id = goLive(register(streamer))

        listOf("   ", "가".repeat(201)).forEach { content ->
            val response = post(id, other, content)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(setOf("content"), (response.body!!["errors"] as Map<*, *>).keys)
        }
    }

    @Test
    fun `LIVE가 아닌 방송에 쓰면 409`() {
        val ready = register(streamer)
        val ended = goLive(register(streamer)).also { unavailable("live/$it") }

        listOf(ready, ended).forEach { assertEquals(HttpStatus.CONFLICT, post(it, other, "안녕").statusCode, "방송 $it") }
    }

    @Test
    fun `없거나 삭제된 방송에 쓰면 404`() {
        val deleted = register(streamer).also { assertEquals(HttpStatus.NO_CONTENT, delete(it, streamer).statusCode) }

        assertEquals(HttpStatus.NOT_FOUND, post(deleted, other, "안녕").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, post(9999, other, "안녕").statusCode)
    }

    @Test
    fun `끝난 방송은 채팅 목록이 비고 없거나 삭제된 방송은 404`() {
        val ended = goLive(register(streamer)).also { post(it, other, "안녕") }.also { unavailable("live/$it") }
        assertEquals(emptyList(), chats(ended))

        assertEquals(HttpStatus.NO_CONTENT, delete(ended, streamer).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, chatsResponse(ended).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, chatsResponse(9999).statusCode)
    }

    @Test
    fun `목록은 최근 50개를 오래된 것부터 준다`() {
        val id = goLive(register(streamer))
        repeat(RECENT_SIZE + 1) { post(id, other, "채팅 $it") }

        val chats = chats(id)
        assertEquals(RECENT_SIZE, chats.size)
        assertEquals("채팅 1", chats.first()["content"])
        assertEquals("채팅 $RECENT_SIZE", chats.last()["content"])
    }

    @Test
    fun `채팅을 쓰면 구독자에게 목록과 같은 모양으로 한 번 보낸다`() {
        val id = goLive(register(streamer))

        subscribe(id).use { events ->
            val chatId = (post(id, other, "안녕하세요").body!!["id"] as Number).toLong()

            assertEquals(SseEvent("chat", """{"id":$chatId,"authorNickname":"테스트시청자","content":"안녕하세요"}"""), events.next())
            assertTrue(events.nothingWithin())
        }
    }

    /** 구독 직후 오는 시청자 수 이벤트는 넘긴다. */
    private fun subscribe(id: Long): SseSubscription =
        openSse("/api/broadcasts/$id/events").also { assertEquals("viewer-count", it.next().name) }

    @Suppress("UNCHECKED_CAST")
    private fun post(id: Long, cookie: String?, content: String): ResponseEntity<Map<String, Any?>> =
        http.post().uri("/api/broadcasts/{id}/chats", id).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("content" to content))
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun chats(id: Long, cookie: String? = other): List<Map<String, Any?>> {
        val response = chatsResponse(id, cookie)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body as List<Map<String, Any?>>
    }

    private fun chatsResponse(id: Long, cookie: String? = other): ResponseEntity<*> =
        http.get().uri("/api/broadcasts/{id}/chats", id).withCookie(cookie).retrieve().toEntity(Any::class.java)

    companion object {
        private const val RECENT_SIZE = 50
    }
}
