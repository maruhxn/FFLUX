package com.maruhxn.fflux.donation.port.toss

import com.maruhxn.fflux.common.auth.PublicApi
import com.maruhxn.fflux.donation.application.provided.DonationProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 토스페이먼츠 결제 상태 웹훅(`PAYMENT_STATUS_CHANGED`). 서명이 없으므로 본문은 신호로만 쓰고, 결제 키로 결제를 다시 조회해 확정한다.
 * 처리할 수 없는 건도 200으로 응답해 토스가 재전송하지 않게 한다. 결제사 조회가 실패하면(5xx 등) 오류로 응답해 재전송을 받는다.
 */
@RestController
@RequestMapping("/api/webhooks/toss")
class TossWebhookController(
    private val donationProcessor: DonationProcessor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PublicApi
    @PostMapping
    fun receive(@RequestBody(required = false) request: TossWebhookRequest?): ResponseEntity<Unit> {
        val paymentKey = request?.data?.paymentKey
        log.info("토스 웹훅 수신: eventType={}", request?.eventType)
        when {
            request?.eventType != PAYMENT_STATUS_CHANGED -> log.info("토스 웹훅: 받지 않는 이벤트 {}", request?.eventType)
            paymentKey.isNullOrBlank() -> log.warn("토스 웹훅: 결제 키 없음")
            else -> donationProcessor.confirmByWebhook(paymentKey)
        }
        return ResponseEntity.ok().build()
    }

    private companion object {
        const val PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED"
    }
}

/** 웹훅 본문 중 쓰는 값만 받는다. [data]는 결제 객체다. */
data class TossWebhookRequest(val eventType: String? = null, val data: TossWebhookPayment? = null)

data class TossWebhookPayment(val paymentKey: String? = null)
