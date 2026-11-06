package com.maruhxn.fflux

import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.common.error.UnauthorizedException
import com.maruhxn.fflux.support.IntegrationTest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.test.assertEquals

class ErrorResponseTest : IntegrationTest() {

    @Test
    fun `대상이 없으면 404 ProblemDetail로 응답한다`() {
        assertProblem("/test/errors/not-found", HttpStatus.NOT_FOUND, "방송을 찾을 수 없습니다.")
    }

    @Test
    fun `상태 위반이면 409 ProblemDetail로 응답한다`() {
        assertProblem("/test/errors/conflict", HttpStatus.CONFLICT, "LIVE 방송은 삭제할 수 없습니다.")
    }

    @Test
    fun `스트리머나 작성자가 아니면 403 ProblemDetail로 응답한다`() {
        assertProblem("/test/errors/forbidden", HttpStatus.FORBIDDEN, "방송의 스트리머만 할 수 있습니다.")
    }

    @Test
    fun `로그인하지 않았으면 401 ProblemDetail로 응답한다`() {
        assertProblem("/test/errors/unauthorized", HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.")
    }

    @Test
    fun `요청 본문 검증에 실패하면 400 ProblemDetail로 응답한다`() {
        val response = http.post().uri("/test/errors/validation")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"title": ""}""")
            .retrieve()
            .toEntity(ProblemDetail::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
        assertEquals(400, response.body?.status)
    }

    private fun assertProblem(path: String, status: HttpStatus, detail: String) {
        val response = http.get().uri(path).retrieve().toEntity(ProblemDetail::class.java)

        assertEquals(status, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
        assertEquals(status.value(), response.body?.status)
        assertEquals(detail, response.body?.detail)
    }
}

data class ValidatedRequest(@NotBlank val title: String)

@RestController
class ErrorTriggerController {

    @GetMapping("/test/errors/{type}")
    fun trigger(@PathVariable type: String): Nothing = when (type) {
        "not-found" -> throw NotFoundException("방송을 찾을 수 없습니다.")
        "conflict" -> throw ConflictException("LIVE 방송은 삭제할 수 없습니다.")
        "forbidden" -> throw ForbiddenException("방송의 스트리머만 할 수 있습니다.")
        "unauthorized" -> throw UnauthorizedException("로그인이 필요합니다.")
        else -> error("unknown type: $type")
    }

    @PostMapping("/test/errors/validation")
    fun validation(@Valid @RequestBody request: ValidatedRequest) = request
}
