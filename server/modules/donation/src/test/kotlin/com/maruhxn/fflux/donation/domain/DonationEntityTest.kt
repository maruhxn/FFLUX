package com.maruhxn.fflux.donation.domain

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DonationEntityTest {

    private val now = LocalDateTime.of(2026, 11, 10, 21, 0)

    private fun request(donorId: Long = DONOR, donatable: Boolean = true, message: DonationMessage? = null) =
        Donation.request(ORDER, BROADCAST, STREAMER, donatable, donorId, "후원자", DonationAmount.of(5_000), message, now)

    @Test
    fun `금액은 1,000원부터 1,000,000원까지 받는다`() {
        assertEquals(1_000, DonationAmount.of(1_000).value)
        assertEquals(1_000_000, DonationAmount.of(1_000_000).value)
        listOf(999L, 1_000_001L, 0L, -1_000L).forEach { value ->
            val e = assertFailsWith<BadRequestException> { DonationAmount.of(value) }
            assertEquals(setOf("amount"), e.errors.keys)
        }
    }

    @Test
    fun `메시지는 앞뒤 공백을 지우고 없거나 공백만이면 없음이다`() {
        assertEquals("응원해요", DonationMessage.of("  응원해요 \n")?.value)
        assertNull(DonationMessage.of(null))
        assertNull(DonationMessage.of("   "))
    }

    @Test
    fun `메시지는 100자까지 받는다`() {
        assertEquals(100, DonationMessage.of("가".repeat(100))?.value?.length)
        val e = assertFailsWith<BadRequestException> { DonationMessage.of("가".repeat(101)) }
        assertEquals(setOf("message"), e.errors.keys)
    }

    @Test
    fun `후원 요청은 PENDING이고 대상은 방송의 스트리머다`() {
        val donation = request(message = DonationMessage.of("응원해요"))

        assertEquals(DonationStatus.PENDING, donation.status)
        assertEquals(STREAMER, donation.streamerId)
        assertEquals("후원자", donation.donorNickname)
        assertEquals("응원해요", donation.message?.value)
        assertNull(donation.paymentKey)
    }

    @Test
    fun `자기 방송에 후원하면 403`() {
        assertFailsWith<ForbiddenException> { request(donorId = STREAMER) }
    }

    @Test
    fun `후원할 수 없는 상태의 방송이면 409`() {
        assertFailsWith<ConflictException> { request(donatable = false) }
    }

    @Test
    fun `자기 방송이면 상태와 상관없이 403이 먼저다`() {
        assertFailsWith<ForbiddenException> { request(donorId = STREAMER, donatable = false) }
    }

    @Test
    fun `후원자가 아니면 확정과 취소를 할 수 없어 403`() {
        assertFailsWith<ForbiddenException> { request().checkDonor(OTHER) }
    }

    @Test
    fun `PENDING 후원은 요청 금액과 같은 결제 금액이면 확정이 필요하다`() {
        assertTrue(request().needsConfirm(5_000))
    }

    @Test
    fun `결제 금액이 요청 금액과 다르면 amount 칸의 400`() {
        val e = assertFailsWith<BadRequestException> { request().needsConfirm(1_000) }
        assertEquals(setOf("amount"), e.errors.keys)
    }

    @Test
    fun `같은 주문을 같은 금액으로 승인한 결제만 이 후원의 결제다`() {
        val donation = request()

        donation.checkPayment(ORDER, paid = true, totalAmount = 5_000)
        assertFailsWith<ConflictException> { donation.checkPayment("order-2", paid = true, totalAmount = 5_000) }
        assertFailsWith<ConflictException> { donation.checkPayment(ORDER, paid = false, totalAmount = 5_000) }
        assertFailsWith<ConflictException> { donation.checkPayment(ORDER, paid = true, totalAmount = 1_000) }
    }

    companion object {
        private const val ORDER = "order-1"
        private const val BROADCAST = 1L
        private const val STREAMER = 10L
        private const val DONOR = 20L
        private const val OTHER = 30L
    }
}
