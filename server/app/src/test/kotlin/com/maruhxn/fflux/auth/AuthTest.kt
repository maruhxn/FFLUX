package com.maruhxn.fflux.auth

import com.maruhxn.fflux.common.auth.LoginMember
import com.maruhxn.fflux.common.auth.PublicApi
import com.maruhxn.fflux.support.IntegrationTest
import com.maruhxn.fflux.support.TEST_STREAMER_PASSWORD
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthTest : IntegrationTest() {

    @Autowired
    private lateinit var testAccountSeeder: TestAccountSeeder

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `아이디 비밀번호 닉네임으로 가입한다`() {
        val response = signUp("newbie01", "password123", "새내기")

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `이미 쓰이는 아이디로 가입하면 409`() {
        signUp("newbie01", "password123", "새내기")

        val response = signUp("newbie01", "password456", "다른사람")

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
    }

    @Test
    fun `형식이 틀리면 가입은 400이고 어느 칸이 틀렸는지 알려 준다`() {
        val response = signUp("a", "short", "")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
        @Suppress("UNCHECKED_CAST")
        val errors = response.body?.properties?.get("errors") as Map<String, String>
        assertEquals(setOf("username", "password", "nickname"), errors.keys)
        assertEquals("비밀번호는 8~64자입니다.", errors["password"])
    }

    @Test
    fun `로그인하면 HttpOnly SameSite=Lax 세션 쿠키를 받고 내 정보를 조회한다`() {
        signUp("newbie01", "password123", "새내기")

        val login = login("newbie01", "password123")

        assertEquals(HttpStatus.OK, login.statusCode)
        val setCookie = login.headers[HttpHeaders.SET_COOKIE].orEmpty().first { it.startsWith("SESSION=") }
        assertTrue(setCookie.contains("HttpOnly"))
        assertTrue(setCookie.contains("SameSite=Lax"))

        val me = me(sessionCookieOf(login.headers))
        assertEquals(HttpStatus.OK, me.statusCode)
        assertEquals("새내기", me.body?.get("nickname"))
        val memberId = assertNotNull(me.body?.get("memberId")).toString()
        val principalNames = jdbcTemplate.queryForList("SELECT PRINCIPAL_NAME FROM SPRING_SESSION", String::class.java)
        assertEquals(listOf(memberId), principalNames)
    }

    @Test
    fun `비밀번호가 틀리면 로그인은 401`() {
        signUp("newbie01", "password123", "새내기")

        val response = login("newbie01", "wrong-password")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
    }

    @Test
    fun `로그인하지 않으면 내 정보 조회는 401 ProblemDetail`() {
        val response = me(null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
    }

    @Test
    fun `로그아웃하면 같은 쿠키로 내 정보 조회가 401`() {
        signUp("newbie01", "password123", "새내기")
        val cookie = sessionCookieOf(login("newbie01", "password123").headers)

        val logout = http.post().uri("/api/auth/logout").header(HttpHeaders.COOKIE, cookie)
            .retrieve().toBodilessEntity()

        assertEquals(HttpStatus.NO_CONTENT, logout.statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, me(cookie).statusCode)
    }

    @Test
    fun `테스트 계정 버튼으로 streamer와 viewer에 로그인한다`() {
        seedTestAccounts()

        listOf("streamer" to "테스트스트리머", "viewer" to "테스트시청자").forEach { (account, nickname) ->
            val login = testLogin(account)
            assertEquals(HttpStatus.OK, login.statusCode)
            assertEquals(nickname, me(sessionCookieOf(login.headers)).body?.get("nickname"))
        }
    }

    @Test
    fun `테스트 계정은 환경변수 비밀번호로도 로그인된다`() {
        seedTestAccounts()

        assertEquals(HttpStatus.OK, login("streamer", TEST_STREAMER_PASSWORD).statusCode)
    }

    @Test
    fun `테스트 계정이 아닌 아이디로는 테스트 로그인할 수 없다`() {
        seedTestAccounts()
        signUp("newbie01", "password123", "새내기")

        assertEquals(HttpStatus.NOT_FOUND, testLogin("newbie01").statusCode)
    }

    @Test
    fun `LoginMember로 로그인한 회원의 ID와 닉네임을 받는다`() {
        signUp("newbie01", "password123", "새내기")
        val cookie = sessionCookieOf(login("newbie01", "password123").headers)
        val memberId = (me(cookie).body?.get("memberId") as Number).toLong()

        val response = http.get().uri("/api/test/login-member").header(HttpHeaders.COOKIE, cookie)
            .retrieve().toEntity(LoginMember::class.java)

        assertEquals(LoginMember(memberId, "새내기"), response.body)
    }

    @Test
    fun `PublicApi가 없는 api는 비로그인이면 401 ProblemDetail`() {
        val response = http.get().uri("/api/test/login-member").retrieve().toEntity(ProblemDetail::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
    }

    @Test
    fun `핸들러가 없는 api 경로도 비로그인이면 401`() {
        val response = http.get().uri("/api/no-such-path").retrieve().toBodilessEntity()

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `PublicApi가 붙은 api는 비로그인도 부를 수 있다`() {
        val response = http.get().uri("/api/test/public").retrieve().toEntity(String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("public", response.body)
    }

    private fun seedTestAccounts() = testAccountSeeder.run(DefaultApplicationArguments())

    private fun signUp(username: String, password: String, nickname: String) =
        http.post().uri("/api/members")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("username" to username, "password" to password, "nickname" to nickname))
            .retrieve()
            .toEntity(ProblemDetail::class.java)

    private fun login(username: String, password: String): ResponseEntity<String> =
        http.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("username" to username, "password" to password))
            .retrieve()
            .toEntity(String::class.java)

    private fun testLogin(account: String): ResponseEntity<String> =
        http.post().uri("/api/auth/test-login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("account" to account))
            .retrieve()
            .toEntity(String::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun me(cookie: String?): ResponseEntity<Map<String, Any>> =
        http.get().uri("/api/auth/me")
            .apply { if (cookie != null) header(HttpHeaders.COOKIE, cookie) }
            .retrieve()
            .toEntity(Map::class.java) as ResponseEntity<Map<String, Any>>
}

@RestController
class AuthTestController {

    @GetMapping("/api/test/login-member")
    fun loginMember(member: LoginMember): LoginMember = member

    @PublicApi
    @GetMapping("/api/test/public")
    fun public(): String = "public"
}
