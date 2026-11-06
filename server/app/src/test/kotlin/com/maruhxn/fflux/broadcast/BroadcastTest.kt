package com.maruhxn.fflux.broadcast

import com.maruhxn.fflux.auth.TestAccountSeeder
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 방송 관리 API의 인가 매트릭스(비로그인 / 타인 / 스트리머)와 상태 조건.
 * LIVE는 송출 흐름(토큰·인증 콜백·훅)을, ENDED는 아직 없는 종료(#8)를 거쳐야 해서 준비 단계에서만 상태를 DB에 직접 쓴다.
 */
class BroadcastTest : IntegrationTest() {

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

    // 등록

    @Test
    fun `로그인한 회원이 방송을 등록하면 그 회원이 스트리머인 READY 방송이 생긴다`() {
        val id = register(streamer, "첫 방송", "설명입니다")

        val detail = detail(id)
        assertEquals(HttpStatus.OK, detail.statusCode)
        val body = detail.body!!
        assertEquals("첫 방송", body["title"])
        assertEquals("설명입니다", body["description"])
        assertEquals("READY", body["status"])
        assertEquals("테스트스트리머", body["streamerNickname"])
        assertEquals(memberIdOf("streamer"), (body["streamerId"] as Number).toLong())
    }

    @Test
    fun `비로그인은 방송을 등록할 수 없다`() {
        assertEquals(HttpStatus.UNAUTHORIZED, registerResponse(null, "첫 방송").statusCode)
    }

    @Test
    fun `제목이 비었거나 50자를 넘으면 등록은 400이고 어느 칸이 틀렸는지 알려 준다`() {
        listOf(" ", "가".repeat(51)).forEach { title ->
            val response = registerResponse(streamer, title)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(setOf("title"), (response.body!!["errors"] as Map<*, *>).keys)
        }
    }

    @Test
    fun `제목 길이는 앞뒤 공백을 지운 뒤 센다`() {
        val title = "가".repeat(50)

        val id = register(streamer, "  $title  ")

        assertEquals(title, detail(id).body!!["title"])
    }

    @Test
    fun `제목과 설명이 모두 틀리면 두 칸의 에러를 한 번에 알려 준다`() {
        val response = registerResponse(streamer, " ", "가".repeat(501))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(setOf("title", "description"), (response.body!!["errors"] as Map<*, *>).keys)
    }

    @Test
    fun `설명이 500자를 넘으면 등록은 400`() {
        assertEquals(HttpStatus.BAD_REQUEST, registerResponse(streamer, "첫 방송", "가".repeat(501)).statusCode)
    }

    @Test
    fun `제목 앞뒤 공백은 지우고 빈 설명은 없음으로 저장한다`() {
        val id = register(streamer, "  첫 방송  ", "   ")

        val body = detail(id).body!!
        assertEquals("첫 방송", body["title"])
        assertNull(body["description"])
    }

    @Test
    fun `스트리머 닉네임은 등록 시점 값으로 남는다`() {
        val id = register(streamer, "첫 방송")
        jdbcTemplate.update("UPDATE member SET nickname = '바뀐닉네임' WHERE username = 'streamer'")

        assertEquals("테스트스트리머", detail(id).body!!["streamerNickname"])
    }

    // 상세

    @Test
    fun `상세는 비로그인과 타인도 본다`() {
        val id = register(streamer, "첫 방송")

        assertEquals(HttpStatus.OK, detail(id, cookie = null).statusCode)
        assertEquals(HttpStatus.OK, detail(id, cookie = other).statusCode)
    }

    @Test
    fun `없는 방송의 상세는 404`() {
        assertEquals(HttpStatus.NOT_FOUND, detail(999_999).statusCode)
    }

    // 수정

    @Test
    fun `스트리머는 READY와 LIVE 방송을 수정한다`() {
        listOf("READY", "LIVE").forEach { status ->
            val id = register(streamer, "원래 제목")
            setStatus(id, status)

            assertEquals(HttpStatus.NO_CONTENT, edit(id, streamer, "바꾼 제목", "바꾼 설명").statusCode, status)
            val body = detail(id).body!!
            assertEquals("바꾼 제목", body["title"])
            assertEquals("바꾼 설명", body["description"])
        }
    }

    @Test
    fun `ENDED 방송 수정은 409`() {
        val id = register(streamer, "원래 제목")
        setStatus(id, "ENDED")

        assertEquals(HttpStatus.CONFLICT, edit(id, streamer, "바꾼 제목").statusCode)
        assertEquals("원래 제목", detail(id).body!!["title"])
    }

    @Test
    fun `비로그인 수정은 401 타인 수정은 403`() {
        val id = register(streamer, "원래 제목")

        assertEquals(HttpStatus.UNAUTHORIZED, edit(id, null, "바꾼 제목").statusCode)
        assertEquals(HttpStatus.FORBIDDEN, edit(id, other, "바꾼 제목").statusCode)
        assertEquals("원래 제목", detail(id).body!!["title"])
    }

    @Test
    fun `타인이 ENDED 방송을 수정하면 409가 아니라 403`() {
        val id = register(streamer, "원래 제목")
        setStatus(id, "ENDED")

        assertEquals(HttpStatus.FORBIDDEN, edit(id, other, "바꾼 제목").statusCode)
    }

    @Test
    fun `잘못된 제목으로 수정하면 없는 방송이나 타인의 방송이어도 400`() {
        val id = register(streamer, "원래 제목")

        assertEquals(HttpStatus.BAD_REQUEST, edit(999_999, streamer, " ").statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, edit(id, other, " ").statusCode)
    }

    @Test
    fun `없는 방송 수정은 404`() {
        assertEquals(HttpStatus.NOT_FOUND, edit(999_999, streamer, "바꾼 제목").statusCode)
    }

    // 삭제

    @Test
    fun `스트리머는 READY와 ENDED 방송을 삭제하고 삭제된 방송의 상세는 404`() {
        listOf("READY", "ENDED").forEach { status ->
            val id = register(streamer, "지울 방송")
            setStatus(id, status)

            assertEquals(HttpStatus.NO_CONTENT, delete(id, streamer).statusCode, status)
            assertEquals(HttpStatus.NOT_FOUND, detail(id).statusCode, status)
        }
    }

    @Test
    fun `LIVE 방송 삭제는 409`() {
        val id = register(streamer, "방송 중")
        setStatus(id, "LIVE")

        assertEquals(HttpStatus.CONFLICT, delete(id, streamer).statusCode)
        assertEquals(HttpStatus.OK, detail(id).statusCode)
    }

    @Test
    fun `비로그인 삭제는 401 타인 삭제는 403`() {
        val id = register(streamer, "지울 방송")

        assertEquals(HttpStatus.UNAUTHORIZED, delete(id, null).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, delete(id, other).statusCode)
        assertEquals(HttpStatus.OK, detail(id).statusCode)
    }

    @Test
    fun `타인이 LIVE 방송을 삭제하면 409가 아니라 403`() {
        val id = register(streamer, "방송 중")
        setStatus(id, "LIVE")

        assertEquals(HttpStatus.FORBIDDEN, delete(id, other).statusCode)
    }

    @Test
    fun `삭제된 방송은 다시 삭제하거나 수정할 수 없다`() {
        val id = register(streamer, "지울 방송")
        delete(id, streamer)

        assertEquals(HttpStatus.NOT_FOUND, delete(id, streamer).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, edit(id, streamer, "바꾼 제목").statusCode)
    }

    // 목록

    @Test
    fun `메인은 LIVE 지난 방송은 ENDED만 최신순으로 비로그인에게도 보여 준다`() {
        val ready = register(streamer, "준비")
        val live1 = register(streamer, "방송1").also { setStatus(it, "LIVE") }
        val ended = register(streamer, "끝남").also { setStatus(it, "ENDED") }
        val live2 = register(other, "방송2").also { setStatus(it, "LIVE") }

        listOf(null, other, streamer).forEach { cookie ->
            assertEquals(listOf(live2, live1), idsOf(list("LIVE", cookie = cookie)))
            assertEquals(listOf(ended), idsOf(list("ENDED", cookie = cookie)))
        }
        assertEquals(false, ready in idsOf(list("LIVE")) + idsOf(list("ENDED")))
    }

    @Test
    fun `공개 목록에 READY나 없는 상태를 요청하거나 상태를 빼면 400`() {
        assertEquals(HttpStatus.BAD_REQUEST, list("READY").statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, list("NOPE").statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, http.get().uri("/api/broadcasts").retrieve().toBodilessEntity().statusCode)
    }

    @Test
    fun `내 방송은 내 READY LIVE ENDED 방송만 보여 주고 비로그인은 401`() {
        val ready = register(streamer, "준비")
        val live = register(streamer, "방송").also { setStatus(it, "LIVE") }
        val ended = register(streamer, "끝남").also { setStatus(it, "ENDED") }
        val othersReady = register(other, "남의 방송")

        assertEquals(listOf(ended, live, ready), idsOf(mine(streamer)))
        assertEquals(listOf(othersReady), idsOf(mine(other)))
        assertEquals(HttpStatus.UNAUTHORIZED, mine(null).statusCode)
    }

    @Test
    fun `삭제된 방송은 어떤 목록에도 나오지 않는다`() {
        val readyDeleted = register(streamer, "준비").also { delete(it, streamer) }
        val endedDeleted = register(streamer, "끝남").also { setStatus(it, "ENDED"); delete(it, streamer) }

        val all = idsOf(list("LIVE")) + idsOf(list("ENDED")) + idsOf(mine(streamer))
        assertEquals(false, readyDeleted in all)
        assertEquals(false, endedDeleted in all)
    }

    @Test
    fun `목록은 20개씩 끊고 nextCursor로 다음 페이지를 읽는다`() {
        val ids = (1..21).map { register(streamer, "방송$it").also { id -> setStatus(id, "LIVE") } }.reversed()

        val first = list("LIVE").body!!
        assertEquals(ids.take(20), idsOf(first))
        val cursor = (first["nextCursor"] as Number).toLong()

        val second = list("LIVE", cursor = cursor).body!!
        assertEquals(ids.drop(20), idsOf(second))
        assertNull(second["nextCursor"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun list(status: String, cursor: Long? = null, cookie: String? = streamer): ResponseEntity<Map<String, Any?>> =
        http.get().uri { it.path("/api/broadcasts").queryParam("status", status).apply { if (cursor != null) queryParam("cursor", cursor) }.build() }
            .withCookie(cookie)
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun mine(cookie: String?): ResponseEntity<Map<String, Any?>> =
        http.get().uri("/api/broadcasts/mine").withCookie(cookie)
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    private fun idsOf(response: ResponseEntity<Map<String, Any?>>): List<Long> = idsOf(response.body!!)

    @Suppress("UNCHECKED_CAST")
    private fun idsOf(page: Map<String, Any?>): List<Long> =
        (page["items"] as List<Map<String, Any?>>).map { (it["id"] as Number).toLong() }

    private fun setStatus(id: Long, status: String) {
        jdbcTemplate.update("UPDATE broadcast SET status = ? WHERE id = ?", status, id)
    }

    private fun edit(id: Long, cookie: String?, title: String, description: String? = null) =
        http.patch().uri("/api/broadcasts/{id}", id).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("title" to title, "description" to description))
            .retrieve().toBodilessEntity()

    private fun delete(id: Long, cookie: String?) =
        http.delete().uri("/api/broadcasts/{id}", id).withCookie(cookie).retrieve().toBodilessEntity()

    private fun testLogin(account: String): String =
        sessionCookieOf(
            http.post().uri("/api/auth/test-login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("account" to account))
                .retrieve().toBodilessEntity().headers,
        )

    private fun memberIdOf(username: String): Long =
        jdbcTemplate.queryForObject("SELECT id FROM member WHERE username = ?", Long::class.java, username)!!

    private fun registerResponse(cookie: String?, title: String, description: String? = null) =
        http.post().uri("/api/broadcasts")
            .withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("title" to title, "description" to description))
            .retrieve().toEntity(Map::class.java)

    private fun register(cookie: String, title: String, description: String? = null): Long {
        val response = registerResponse(cookie, title, description)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return (response.body!!["id"] as Number).toLong()
    }

    @Suppress("UNCHECKED_CAST")
    private fun detail(id: Long, cookie: String? = streamer): ResponseEntity<Map<String, Any?>> =
        http.get().uri("/api/broadcasts/{id}", id).withCookie(cookie)
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    private fun <S : org.springframework.web.client.RestClient.RequestHeadersSpec<S>> S.withCookie(cookie: String?): S =
        apply { if (cookie != null) header(HttpHeaders.COOKIE, cookie) }
}
