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

/**
 * 방송 검색(공개 목록의 `q`, `streamerId`)과 스트리머 검색. 테스트 계정 닉네임은 `테스트스트리머`, `테스트시청자`다.
 * LIVE·ENDED는 송출 흐름을 거쳐야 해서 준비 단계에서만 상태를 DB에 직접 쓴다.
 */
class SearchTest : IntegrationTest() {

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

    // 방송 검색

    @Test
    fun `검색어가 제목에 들어간 방송만 비로그인에게도 최신순으로 보여 준다`() {
        val match1 = register(streamer, "롤 랭크 게임").also { setStatus(it, "LIVE") }
        register(streamer, "저스트 채팅").also { setStatus(it, "LIVE") }
        val match2 = register(other, "새벽 롤").also { setStatus(it, "LIVE") }

        assertEquals(listOf(match2, match1), idsOf(broadcasts("LIVE", q = "롤", cookie = null)))
    }

    @Test
    fun `검색어가 스트리머 닉네임에 들어간 방송도 찾는다`() {
        val streamers = register(streamer, "저스트 채팅").also { setStatus(it, "LIVE") }
        register(other, "저스트 채팅").also { setStatus(it, "LIVE") }

        assertEquals(listOf(streamers), idsOf(broadcasts("LIVE", q = "스트리머")))
    }

    @Test
    fun `영문 검색어는 대소문자를 가리지 않는다`() {
        val id = register(streamer, "LoL 랭크").also { setStatus(it, "ENDED") }

        assertEquals(listOf(id), idsOf(broadcasts("ENDED", q = "lol")))
    }

    @Test
    fun `검색어의 퍼센트와 밑줄은 글자 그대로 찾는다`() {
        val percent = register(streamer, "승률 100%").also { setStatus(it, "LIVE") }
        val underscore = register(streamer, "a_b").also { setStatus(it, "LIVE") }
        register(streamer, "승률 1000").also { setStatus(it, "LIVE") }
        register(streamer, "axb").also { setStatus(it, "LIVE") }

        assertEquals(listOf(percent), idsOf(broadcasts("LIVE", q = "0%")))
        assertEquals(listOf(underscore), idsOf(broadcasts("LIVE", q = "_")))
    }

    @Test
    fun `검색 결과에도 READY와 삭제된 방송은 나오지 않는다`() {
        val ready = register(streamer, "롤 준비")
        val deleted = register(streamer, "롤 끝남").also { setStatus(it, "ENDED"); delete(it, streamer) }

        val found = idsOf(broadcasts("LIVE", q = "롤")) + idsOf(broadcasts("ENDED", q = "롤"))
        assertEquals(false, ready in found)
        assertEquals(false, deleted in found)
    }

    @Test
    fun `검색어 앞뒤 공백은 지우고 공백뿐인 검색어는 조건이 없는 것이다`() {
        val lol = register(streamer, "롤 랭크").also { setStatus(it, "LIVE") }
        val chat = register(streamer, "저스트 채팅").also { setStatus(it, "LIVE") }

        assertEquals(listOf(lol), idsOf(broadcasts("LIVE", q = "  롤  ")))
        assertEquals(listOf(chat, lol), idsOf(broadcasts("LIVE", q = "   ")))
    }

    @Test
    fun `검색어가 50자를 넘으면 400이고 q 칸이 틀렸다고 알려 준다`() {
        val response = broadcasts("LIVE", q = "가".repeat(51))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(setOf("q"), (response.body!!["errors"] as Map<*, *>).keys)
    }

    @Test
    fun `streamerId를 주면 그 스트리머의 방송만 보여 주고 검색어와 함께 쓸 수 있다`() {
        val streamerId = memberIdOf("streamer")
        val lol = register(streamer, "롤 랭크").also { setStatus(it, "ENDED") }
        val chat = register(streamer, "저스트 채팅").also { setStatus(it, "ENDED") }
        register(other, "롤 새벽").also { setStatus(it, "ENDED") }

        assertEquals(listOf(chat, lol), idsOf(broadcasts("ENDED", streamerId = streamerId, cookie = null)))
        assertEquals(listOf(lol), idsOf(broadcasts("ENDED", q = "롤", streamerId = streamerId)))
    }

    // 스트리머 검색

    @Test
    fun `닉네임에 검색어가 든 스트리머를 방송 중인 스트리머부터 비로그인에게도 보여 준다`() {
        register(streamer, "지난 방송").also { setStatus(it, "ENDED") }
        val live = register(other, "방송 중").also { setStatus(it, "LIVE") }
        register(other, "지난 방송").also { setStatus(it, "ENDED") }

        val found = streamers("테스트", cookie = null)

        assertEquals(HttpStatus.OK, found.statusCode)
        assertEquals(
            listOf(
                mapOf("streamerId" to memberIdOf("viewer"), "nickname" to "테스트시청자", "liveBroadcastId" to live),
                mapOf("streamerId" to memberIdOf("streamer"), "nickname" to "테스트스트리머", "liveBroadcastId" to null),
            ),
            streamersOf(found),
        )
    }

    @Test
    fun `방송 중이 아니면 최근에 방송한 스트리머부터 보여 준다`() {
        register(streamer, "첫 방송").also { setStatus(it, "ENDED") }
        register(other, "둘째 방송").also { setStatus(it, "ENDED") }

        assertEquals(listOf("테스트시청자", "테스트스트리머"), streamersOf(streamers("테스트")).map { it["nickname"] })
    }

    @Test
    fun `맞는 스트리머가 20명을 넘어도 방송 중인 스트리머는 빠지지 않고 맨 앞에 온다`() {
        val live = register(streamer, "오래전에 등록한 방송").also { setStatus(it, "LIVE") }
        // 스트리머 검색은 방송 표만 본다. 회원 20명을 가입시키는 대신 다른 스트리머의 더 최근 방송을 직접 넣는다.
        (1..20).forEach {
            jdbcTemplate.update(
                "INSERT INTO broadcast (streamer_id, streamer_nickname, title, status, created_at) VALUES (?, ?, '지난 방송', 'ENDED', NOW(6))",
                1_000L + it, "테스트$it",
            )
        }

        val found = streamersOf(streamers("테스트"))

        assertEquals(20, found.size)
        assertEquals(live, found.first()["liveBroadcastId"])
    }

    @Test
    fun `제목만 맞거나 공개 방송이 없는 회원은 스트리머 검색에 나오지 않는다`() {
        register(streamer, "준비 중")
        register(other, "테스트 방송").also { setStatus(it, "ENDED"); delete(it, other) }
        register(other, "테스트 방송").also { setStatus(it, "LIVE") }

        assertEquals(listOf("테스트시청자"), streamersOf(streamers("테스트")).map { it["nickname"] })
        assertEquals(emptyList(), streamersOf(streamers("스트리머")))
    }

    @Test
    fun `스트리머 검색어가 없거나 50자를 넘으면 400`() {
        listOf(null, "   ", "가".repeat(51)).forEach { q ->
            val status = http.get().uri { it.path("/api/streamers").apply { if (q != null) queryParam("q", q) }.build() }
                .retrieve().toBodilessEntity().statusCode
            assertEquals(HttpStatus.BAD_REQUEST, status, q)
        }
    }

    // 스트리머 정보

    @Test
    fun `스트리머 정보는 닉네임과 방송 중인 방송을 비로그인에게도 보여 준다`() {
        val streamerId = memberIdOf("streamer")
        register(streamer, "지난 방송").also { setStatus(it, "ENDED") }

        assertEquals(
            mapOf("streamerId" to streamerId, "nickname" to "테스트스트리머", "liveBroadcastId" to null),
            normalized(streamer(streamerId, cookie = null).body!!),
        )

        val live = register(streamer, "방송 중").also { setStatus(it, "LIVE") }
        assertEquals(live, normalized(streamer(streamerId).body!!)["liveBroadcastId"])
    }

    @Test
    fun `공개 방송이 없는 회원이나 없는 회원의 스트리머 정보는 404`() {
        register(streamer, "준비 중")

        assertEquals(HttpStatus.NOT_FOUND, streamer(memberIdOf("streamer")).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, streamer(memberIdOf("viewer")).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, streamer(999_999).statusCode)
    }

    @Suppress("UNCHECKED_CAST")
    private fun streamers(q: String?, cookie: String? = null): ResponseEntity<List<Map<String, Any?>>> =
        http.get().uri { it.path("/api/streamers").apply { if (q != null) queryParam("q", q) }.build() }
            .apply { if (cookie != null) header(HttpHeaders.COOKIE, cookie) }
            .retrieve().toEntity(List::class.java) as ResponseEntity<List<Map<String, Any?>>>

    private fun streamersOf(response: ResponseEntity<List<Map<String, Any?>>>): List<Map<String, Any?>> {
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!.map(::normalized)
    }

    private fun streamer(streamerId: Long, cookie: String? = null): ResponseEntity<Map<String, Any?>> = get("/api/streamers/$streamerId", cookie)

    /** JSON 숫자는 크기에 따라 Integer·Long으로 읽히므로 ID를 Long으로 맞춘다. */
    private fun normalized(item: Map<String, Any?>): Map<String, Any?> =
        item.mapValues { (_, value) -> if (value is Number) value.toLong() else value }

    private fun broadcasts(status: String, q: String? = null, streamerId: Long? = null, cookie: String? = null): ResponseEntity<Map<String, Any?>> =
        get(
            "/api/broadcasts",
            cookie,
            "status" to status,
            "q" to q,
            "streamerId" to streamerId,
        )

    @Suppress("UNCHECKED_CAST")
    private fun get(path: String, cookie: String?, vararg params: Pair<String, Any?>): ResponseEntity<Map<String, Any?>> =
        http.get().uri {
            it.path(path).apply { params.forEach { (key, value) -> if (value != null) queryParam(key, value) } }.build()
        }
            .apply { if (cookie != null) header(HttpHeaders.COOKIE, cookie) }
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun idsOf(response: ResponseEntity<Map<String, Any?>>): List<Long> {
        assertEquals(HttpStatus.OK, response.statusCode)
        return (response.body!!["items"] as List<Map<String, Any?>>).map { (it["id"] as Number).toLong() }
    }

    private fun register(cookie: String, title: String): Long {
        val response = http.post().uri("/api/broadcasts")
            .header(HttpHeaders.COOKIE, cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("title" to title, "description" to null))
            .retrieve().toEntity(Map::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return (response.body!!["id"] as Number).toLong()
    }

    private fun delete(id: Long, cookie: String) {
        val response = http.delete().uri("/api/broadcasts/{id}", id).header(HttpHeaders.COOKIE, cookie).retrieve().toBodilessEntity()
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    private fun memberIdOf(username: String): Long =
        jdbcTemplate.queryForObject("SELECT id FROM member WHERE username = ?", Long::class.java, username)!!

    private fun setStatus(id: Long, status: String) {
        jdbcTemplate.update("UPDATE broadcast SET status = ? WHERE id = ?", status, id)
    }

    private fun testLogin(account: String): String =
        sessionCookieOf(
            http.post().uri("/api/auth/test-login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("account" to account))
                .retrieve().toBodilessEntity().headers,
        )
}
