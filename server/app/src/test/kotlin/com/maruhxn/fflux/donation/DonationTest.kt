package com.maruhxn.fflux.donation

import com.maruhxn.fflux.broadcast.BroadcastFlowTest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 후원: 요청 조건(방송 상태, 자기 방송, 금액·메시지), 결제 승인 확정과 멱등, 결제창 실패 취소, 인가. */
class DonationTest : BroadcastFlowTest() {

    // 요청

    @Test
    fun `로그인 회원이 LIVE 방송에 후원을 요청하면 스트리머를 대상으로 PENDING이 생긴다`() {
        val id = goLive(register(streamer))

        val response = request(id, other, 5_000, "  응원해요 ")

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val orderId = orderIdOf(response)
        assertEquals(5_000, (response.body!!["amount"] as Number).toLong())
        val row = row(orderId)
        assertEquals("PENDING", row["status"])
        assertEquals(memberId("streamer"), (row["streamer_id"] as Number).toLong())
        assertEquals("테스트시청자", row["donor_nickname"])
        assertEquals("응원해요", row["message"])
    }

    @Test
    fun `끝난 방송에도 후원할 수 있고 공백만인 메시지는 없음이다`() {
        val id = ended()

        val response = request(id, other, 1_000, "   ")

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNull(row(orderIdOf(response))["message"])
    }

    @Test
    fun `READY 방송에 후원하면 409`() {
        assertEquals(HttpStatus.CONFLICT, request(register(streamer), other, 1_000).statusCode)
    }

    @Test
    fun `자기 방송에 후원하면 403`() {
        assertEquals(HttpStatus.FORBIDDEN, request(goLive(register(streamer)), streamer, 1_000).statusCode)
    }

    @Test
    fun `비로그인 후원은 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, request(goLive(register(streamer)), null, 1_000).statusCode)
    }

    @Test
    fun `없거나 삭제된 방송에 후원하면 404`() {
        val deleted = ended().also { assertEquals(HttpStatus.NO_CONTENT, delete(it, streamer).statusCode) }

        assertEquals(HttpStatus.NOT_FOUND, request(deleted, other, 1_000).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, request(9999, other, 1_000).statusCode)
    }

    @Test
    fun `금액 범위 밖과 100자를 넘는 메시지는 칸별 400을 한 번에 준다`() {
        val id = goLive(register(streamer))

        val response = request(id, other, 999, "가".repeat(101))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(setOf("amount", "message"), (response.body!!["errors"] as Map<*, *>).keys)
        assertEquals(HttpStatus.BAD_REQUEST, request(id, other, 1_000_001).statusCode)
    }

    // 확정

    @Test
    fun `결제창 승인 뒤 확정하면 토스 승인을 거쳐 DONE이 된다`() {
        val id = goLive(register(streamer))
        val orderId = orderIdOf(request(id, other, 5_000))

        val response = confirm(orderId, other, "pay-1", 5_000)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(id, (response.body!!["broadcastId"] as Number).toLong())
        val row = row(orderId)
        assertEquals("DONE", row["status"])
        assertEquals("pay-1", row["payment_key"])
        assertEquals(listOf("pay-1"), paymentGateway.confirmed)
    }

    @Test
    fun `요청과 다른 금액으로 확정하면 400이고 토스를 부르지 않는다`() {
        val orderId = orderIdOf(request(goLive(register(streamer)), other, 5_000))

        assertEquals(HttpStatus.BAD_REQUEST, confirm(orderId, other, "pay-1", 1_000).statusCode)

        assertEquals("PENDING", row(orderId)["status"])
        assertTrue(paymentGateway.confirmed.isEmpty())
    }

    @Test
    fun `이미 DONE인 후원을 다시 확정하면 승인을 다시 하지 않고 성공한다`() {
        val orderId = orderIdOf(request(goLive(register(streamer)), other, 5_000))
        assertEquals(HttpStatus.OK, confirm(orderId, other, "pay-1", 5_000).statusCode)
        val confirmedAt = row(orderId)["confirmed_at"]

        assertEquals(HttpStatus.OK, confirm(orderId, other, "pay-1", 5_000).statusCode)

        assertEquals(listOf("pay-1"), paymentGateway.confirmed)
        assertEquals(confirmedAt, row(orderId)["confirmed_at"])
    }

    @Test
    fun `같은 결제를 동시에 두 번 확정해도 둘 다 성공하고 DONE은 한 번이다`() {
        val orderId = orderIdOf(request(goLive(register(streamer)), other, 5_000))
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val responses = try {
            List(2) { executor.submit(Callable { start.await(); confirm(orderId, other, "pay-1", 5_000) }) }
                .also { start.countDown() }
                .map { it.get() }
        } finally {
            executor.shutdown()
        }

        responses.forEach { assertEquals(HttpStatus.OK, it.statusCode) }
        assertEquals("DONE", row(orderId)["status"])
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM donation WHERE status = 'DONE'", Int::class.java))
    }

    @Test
    fun `승인 응답이 유실돼 다시 승인하면 ALREADY_PROCESSED_PAYMENT를 조회로 확인해 확정한다`() {
        val orderId = orderIdOf(request(goLive(register(streamer)), other, 5_000))
        paymentGateway.approvedElsewhere("pay-1", orderId, 5_000)

        assertEquals(HttpStatus.OK, confirm(orderId, other, "pay-1", 5_000).statusCode)

        assertEquals("DONE", row(orderId)["status"])
    }

    @Test
    fun `조회한 결제가 후원과 다르면 확정하지 않고 409`() {
        val id = goLive(register(streamer))
        val orderId = orderIdOf(request(id, other, 5_000))
        val otherOrderId = orderIdOf(request(id, other, 5_000))
        paymentGateway.approvedElsewhere("pay-1", otherOrderId, 5_000)
        paymentGateway.approvedElsewhere("pay-2", orderId, 1_000)

        assertEquals(HttpStatus.CONFLICT, confirm(orderId, other, "pay-1", 5_000).statusCode)
        assertEquals(HttpStatus.CONFLICT, confirm(orderId, other, "pay-2", 5_000).statusCode)

        assertEquals("PENDING", row(orderId)["status"])
    }

    @Test
    fun `토스가 승인을 거절하면 409이고 PENDING으로 남는다`() {
        val orderId = orderIdOf(request(goLive(register(streamer)), other, 5_000))
        paymentGateway.rejecting = true

        val response = confirm(orderId, other, "pay-1", 5_000)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue((response.body!!["detail"] as String).contains("카드사에서 거절했습니다."))
        assertEquals("PENDING", row(orderId)["status"])
    }

    // 취소

    @Test
    fun `결제창에서 실패해 돌아오면 CANCELED가 되고 다시 취소해도 성공한다`() {
        val orderId = orderIdOf(request(goLive(register(streamer)), other, 5_000))

        assertEquals(HttpStatus.OK, cancel(orderId, other).statusCode)
        assertEquals(HttpStatus.OK, cancel(orderId, other).statusCode)

        assertEquals("CANCELED", row(orderId)["status"])
    }

    @Test
    fun `취소된 후원은 확정할 수 없고 확정된 후원은 취소할 수 없어 409`() {
        val id = goLive(register(streamer))
        val canceled = orderIdOf(request(id, other, 5_000)).also { cancel(it, other) }
        val done = orderIdOf(request(id, other, 5_000)).also { confirm(it, other, "pay-1", 5_000) }

        assertEquals(HttpStatus.CONFLICT, confirm(canceled, other, "pay-2", 5_000).statusCode)
        assertEquals(HttpStatus.CONFLICT, cancel(done, other).statusCode)
        assertEquals("CANCELED", row(canceled)["status"])
        assertEquals("DONE", row(done)["status"])
    }

    // 인가

    @Test
    fun `확정과 취소는 비로그인 401, 후원자가 아니면 403, 없는 주문은 404`() {
        val orderId = orderIdOf(request(goLive(register(streamer)), other, 5_000))

        assertEquals(HttpStatus.UNAUTHORIZED, confirm(orderId, null, "pay-1", 5_000).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, cancel(orderId, null).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, confirm(orderId, streamer, "pay-1", 5_000).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, cancel(orderId, streamer).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, confirm("no-such-order", other, "pay-1", 5_000).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, cancel("no-such-order", other).statusCode)
        assertEquals("PENDING", row(orderId)["status"])
        assertTrue(paymentGateway.confirmed.isEmpty())
    }

    private fun ended(): Long = goLive(register(streamer)).also { unavailable("live/$it") }

    @Suppress("UNCHECKED_CAST")
    private fun request(id: Long, cookie: String?, amount: Long, message: String? = null): ResponseEntity<Map<String, Any?>> =
        http.post().uri("/api/broadcasts/{id}/donations", id).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("amount" to amount, "message" to message))
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun confirm(orderId: String, cookie: String?, paymentKey: String, amount: Long): ResponseEntity<Map<String, Any?>> =
        http.post().uri("/api/donations/{orderId}/confirm", orderId).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("paymentKey" to paymentKey, "amount" to amount))
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    private fun cancel(orderId: String, cookie: String?): ResponseEntity<Map<*, *>> =
        http.post().uri("/api/donations/{orderId}/cancel", orderId).withCookie(cookie)
            .retrieve().toEntity(Map::class.java)

    private fun orderIdOf(response: ResponseEntity<Map<String, Any?>>): String {
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!["orderId"] as String
    }

    private fun row(orderId: String): Map<String, Any?> =
        jdbcTemplate.queryForMap("SELECT * FROM donation WHERE order_id = ?", orderId)

    private fun memberId(username: String): Long =
        jdbcTemplate.queryForObject("SELECT id FROM member WHERE username = ?", Long::class.java, username)!!
}
