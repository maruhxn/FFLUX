package com.maruhxn.fflux.donation

import com.maruhxn.fflux.broadcast.BroadcastFlowTest
import com.maruhxn.fflux.support.SseEvent
import com.maruhxn.fflux.support.SseSubscription
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 방송별 후원 내역·합계(DONE만, 비로그인 허용)와 LIVE 중 확정된 후원의 SSE 알림. */
class DonationHistoryTest : BroadcastFlowTest() {

    @Test
    fun `내역은 확정된 후원만 최신순으로 닉네임·금액·메시지·시각을 주고 합계를 함께 준다`() {
        val id = goLive(register(streamer))
        donate(id, 1_000, "첫 후원")
        donate(id, 5_000, null)
        pending(id, 10_000)
        pending(id, 10_000).also { cancel(it) }

        val page = history(id, cookie = null)
        // 로그인한 회원과 스트리머도 같은 내역을 본다.
        assertEquals(page, history(id, cookie = other))
        assertEquals(page, history(id, cookie = streamer))

        assertEquals(6_000, (page["totalAmount"] as Number).toLong())
        assertNull(page["nextCursor"])
        val items = items(page)
        assertEquals(listOf(5_000L, 1_000L), items.map { (it["amount"] as Number).toLong() })
        assertEquals(listOf(null, "첫 후원"), items.map { it["message"] })
        items.forEach {
            assertEquals("테스트시청자", it["donorNickname"])
            assertNotNull(it["confirmedAt"])
        }
    }

    @Test
    fun `내역은 커서로 다음 페이지를 읽고 합계는 페이지와 상관없이 전체다`() {
        val id = goLive(register(streamer))
        repeat(PAGE_SIZE + 1) { donate(id, 1_000, "후원 $it") }

        val first = history(id)
        val second = history(id, cursor = (first["nextCursor"] as Number).toLong())

        assertEquals(PAGE_SIZE, items(first).size)
        assertEquals(listOf("후원 0"), items(second).map { it["message"] })
        assertNull(second["nextCursor"])
        assertEquals((PAGE_SIZE + 1) * 1_000L, (second["totalAmount"] as Number).toLong())
    }

    @Test
    fun `방송을 삭제해도 후원 기록은 남는다`() {
        val id = goLive(register(streamer)).also { donate(it, 5_000, "응원해요") }
        unavailable("live/$id")
        assertEquals(HttpStatus.NO_CONTENT, delete(id, streamer).statusCode)

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM donation WHERE broadcast_id = ? AND status = 'DONE'", Int::class.java, id))
    }

    @Test
    fun `LIVE 방송에서 후원이 확정되면 구독자에게 닉네임·금액·메시지를 한 번 보낸다`() {
        val id = goLive(register(streamer))
        val orderId = pending(id, 5_000, "응원해요")

        subscribe(id).use { events ->
            confirm(orderId, "pay-$orderId")
            confirm(orderId, "pay-$orderId")

            assertEquals(SseEvent("donation", """{"donorNickname":"테스트시청자","amount":5000,"message":"응원해요"}"""), events.next())
            assertTrue(events.nothingWithin())
        }
    }

    @Test
    fun `웹훅으로 확정돼도 알림을 보낸다`() {
        val id = goLive(register(streamer))
        val orderId = pending(id, 1_000)
        paymentGateway.approvedElsewhere("pay-1", orderId, 1_000)

        subscribe(id).use { events ->
            http.post().uri("/api/webhooks/toss")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("eventType" to "PAYMENT_STATUS_CHANGED", "data" to mapOf("paymentKey" to "pay-1", "orderId" to orderId)))
                .retrieve().toBodilessEntity()

            assertEquals(SseEvent("donation", """{"donorNickname":"테스트시청자","amount":1000,"message":null}"""), events.next())
        }
    }

    @Test
    fun `끝난 방송에 후원하면 알림을 보내지 않는다`() {
        val id = goLive(register(streamer)).also { unavailable("live/$it") }
        val orderId = pending(id, 1_000)

        subscribe(id).use { events ->
            confirm(orderId, "pay-1")

            assertTrue(events.nothingWithin())
        }
    }

    /** 구독 직후 오는 시청자 수 이벤트는 넘긴다. */
    private fun subscribe(id: Long): SseSubscription =
        openSse("/api/broadcasts/$id/events").also { assertEquals("viewer-count", it.next().name) }

    private fun donate(id: Long, amount: Long, message: String?) {
        val orderId = pending(id, amount, message)
        confirm(orderId, "pay-$orderId", amount)
    }

    private fun pending(id: Long, amount: Long, message: String? = null): String {
        val response = http.post().uri("/api/broadcasts/{id}/donations", id).withCookie(other)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("amount" to amount, "message" to message))
            .retrieve().toEntity(Map::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.body!!["orderId"] as String
    }

    private fun confirm(orderId: String, paymentKey: String, amount: Long? = null) {
        val total = amount ?: (jdbcTemplate.queryForObject("SELECT amount FROM donation WHERE order_id = ?", Long::class.java, orderId)!!)
        val response = http.post().uri("/api/donations/{orderId}/confirm", orderId).withCookie(other)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("paymentKey" to paymentKey, "amount" to total))
            .retrieve().toBodilessEntity()
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    private fun cancel(orderId: String) {
        http.post().uri("/api/donations/{orderId}/cancel", orderId).withCookie(other).retrieve().toBodilessEntity()
    }

    @Suppress("UNCHECKED_CAST")
    private fun history(id: Long, cursor: Long? = null, cookie: String? = other): Map<String, Any?> {
        val response = http.get()
            .uri { it.path("/api/broadcasts/{id}/donations").apply { if (cursor != null) queryParam("cursor", cursor) }.build(id) }
            .withCookie(cookie)
            .retrieve().toEntity(Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun items(page: Map<String, Any?>): List<Map<String, Any?>> = page["items"] as List<Map<String, Any?>>

    companion object {
        private const val PAGE_SIZE = 20
    }
}
