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

/**
 * 토스 결제 상태 웹훅: 본문은 신호로만 쓰고 결제 조회 결과로 확정한다. 처리할 수 없는 건도 200으로 응답한다.
 * successUrl 확정이 유실된 경우(토스에서는 승인됐는데 우리 후원은 PENDING)를 [approvedElsewhere]로 만든다.
 */
class DonationWebhookTest : BroadcastFlowTest() {

    @Test
    fun `승인된 결제의 웹훅을 받으면 로그인 없이도 PENDING 후원이 DONE이 된다`() {
        val orderId = pending(5_000)
        paymentGateway.approvedElsewhere("pay-1", orderId, 5_000)

        assertEquals(HttpStatus.OK, webhook("pay-1", orderId, status = "DONE", amount = 5_000).statusCode)
        // 로그인 쿠키가 실려 와도 같다(공개 경로).
        assertEquals(HttpStatus.OK, webhook("pay-1", orderId, cookie = other).statusCode)

        val row = row(orderId)
        assertEquals("DONE", row["status"])
        assertEquals("pay-1", row["payment_key"])
    }

    @Test
    fun `본문과 달리 조회한 결제가 승인되지 않았거나 금액이 다르면 확정하지 않는다`() {
        val notPaid = pending(5_000).also { paymentGateway.approvedElsewhere("pay-1", it, 5_000, paid = false) }
        val otherAmount = pending(5_000).also { paymentGateway.approvedElsewhere("pay-2", it, 1_000) }

        assertEquals(HttpStatus.OK, webhook("pay-1", notPaid, status = "DONE", amount = 5_000).statusCode)
        assertEquals(HttpStatus.OK, webhook("pay-2", otherAmount, status = "DONE", amount = 5_000).statusCode)

        assertEquals("PENDING", row(notPaid)["status"])
        assertEquals("PENDING", row(otherAmount)["status"])
    }

    @Test
    fun `본문의 orderId가 아니라 조회한 결제의 주문을 확정한다`() {
        val paid = pending(5_000).also { paymentGateway.approvedElsewhere("pay-1", it, 5_000) }
        val forged = pending(5_000)

        assertEquals(HttpStatus.OK, webhook("pay-1", forged, status = "DONE", amount = 5_000).statusCode)

        assertEquals("DONE", row(paid)["status"])
        assertEquals("PENDING", row(forged)["status"])
    }

    @Test
    fun `같은 웹훅을 다시 받아도 성공하고 확정은 한 번이다`() {
        val orderId = pending(5_000)
        paymentGateway.approvedElsewhere("pay-1", orderId, 5_000)
        assertEquals(HttpStatus.OK, webhook("pay-1", orderId).statusCode)
        val confirmedAt = row(orderId)["confirmed_at"]

        assertEquals(HttpStatus.OK, webhook("pay-1", orderId).statusCode)

        assertEquals("DONE", row(orderId)["status"])
        assertEquals(confirmedAt, row(orderId)["confirmed_at"])
    }

    @Test
    fun `successUrl 확정과 웹훅이 동시에 와도 둘 다 성공하고 DONE은 한 번이다`() {
        val orderId = pending(5_000)
        paymentGateway.approvedElsewhere("pay-1", orderId, 5_000)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val responses = try {
            listOf(
                executor.submit(Callable { start.await(); confirm(orderId, "pay-1", 5_000) }),
                executor.submit(Callable { start.await(); webhook("pay-1", orderId) }),
            ).also { start.countDown() }.map { it.get() }
        } finally {
            executor.shutdown()
        }

        responses.forEach { assertEquals(HttpStatus.OK, it.statusCode) }
        assertEquals("DONE", row(orderId)["status"])
        assertEquals(1, doneCount())
    }

    @Test
    fun `토스에 없는 결제, 모르는 주문, 취소된 후원, 다른 이벤트, 결제 키 없는 본문, 빈 본문도 200이고 아무것도 바꾸지 않는다`() {
        val canceled = pending(5_000).also { cancel(it) }
        paymentGateway.approvedElsewhere("pay-canceled", canceled, 5_000)
        paymentGateway.approvedElsewhere("pay-unknown", "no-such-order", 5_000)

        assertEquals(HttpStatus.OK, webhook("no-such-payment", "no-such-order").statusCode)
        assertEquals(HttpStatus.OK, webhook("pay-unknown", "no-such-order").statusCode)
        assertEquals(HttpStatus.OK, webhook("pay-canceled", canceled).statusCode)
        assertEquals(HttpStatus.OK, post(mapOf("eventType" to "DEPOSIT_CALLBACK", "data" to mapOf("paymentKey" to "pay-canceled"))).statusCode)
        assertEquals(HttpStatus.OK, post(mapOf("eventType" to "PAYMENT_STATUS_CHANGED")).statusCode)
        assertEquals(HttpStatus.OK, http.post().uri("/api/webhooks/toss").retrieve().toBodilessEntity().statusCode)

        assertEquals("CANCELED", row(canceled)["status"])
        assertEquals(0, doneCount())
    }

    private fun pending(amount: Long): String {
        val id = jdbcTemplate.queryForList("SELECT id FROM broadcast WHERE status = 'LIVE'", Long::class.java).firstOrNull()
            ?: goLive(register(streamer))
        val response = http.post().uri("/api/broadcasts/{id}/donations", id).withCookie(other)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("amount" to amount))
            .retrieve().toEntity(Map::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!["orderId"] as String
    }

    /** 토스가 보내는 결제 상태 웹훅 형태. data는 결제 객체이고, 서버는 그중 paymentKey만 믿는다. */
    private fun webhook(
        paymentKey: String,
        orderId: String,
        status: String = "DONE",
        amount: Long = 5_000,
        cookie: String? = null,
    ): ResponseEntity<Void> =
        post(
            mapOf(
                "eventType" to "PAYMENT_STATUS_CHANGED",
                "createdAt" to "2026-11-10T21:00:00.000000",
                "data" to mapOf("paymentKey" to paymentKey, "orderId" to orderId, "status" to status, "totalAmount" to amount),
            ),
            cookie,
        )

    private fun post(body: Map<String, Any?>, cookie: String? = null): ResponseEntity<Void> =
        http.post().uri("/api/webhooks/toss").withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().toBodilessEntity()

    private fun confirm(orderId: String, paymentKey: String, amount: Long): ResponseEntity<Void> =
        http.post().uri("/api/donations/{orderId}/confirm", orderId).withCookie(other)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("paymentKey" to paymentKey, "amount" to amount))
            .retrieve().toBodilessEntity()

    private fun cancel(orderId: String) {
        val response = http.post().uri("/api/donations/{orderId}/cancel", orderId).withCookie(other).retrieve().toBodilessEntity()
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    private fun doneCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM donation WHERE status = 'DONE'", Int::class.java)!!

    private fun row(orderId: String): Map<String, Any?> =
        jdbcTemplate.queryForMap("SELECT * FROM donation WHERE order_id = ?", orderId)
}
