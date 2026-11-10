package com.maruhxn.fflux.donation.port.toss

import com.maruhxn.fflux.donation.application.required.Payment
import com.maruhxn.fflux.donation.application.required.PaymentApproval
import com.maruhxn.fflux.donation.application.required.PaymentGateway
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.Base64

/** 토스페이먼츠 결제 승인(`/v1/payments/confirm`)·조회(`/v1/payments/{paymentKey}`). 시크릿 키로 Basic 인증한다. */
@Component
class TossPaymentsClient(properties: TossPaymentsProperties) : PaymentGateway {

    private val restClient = RestClient.builder()
        .baseUrl(properties.apiUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString("${properties.secretKey}:".toByteArray()))
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(CONNECT_TIMEOUT)
                setReadTimeout(READ_TIMEOUT)
            },
        )
        .build()

    override fun confirm(paymentKey: String, orderId: String, amount: Long): PaymentApproval =
        restClient.post().uri("/v1/payments/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("paymentKey" to paymentKey, "orderId" to orderId, "amount" to amount))
            .exchange { _, response ->
                when {
                    response.statusCode.is2xxSuccessful -> PaymentApproval.Approved(response.bodyTo(TossPayment::class.java).toPayment())
                    response.statusCode.is4xxClientError -> {
                        val error = response.bodyTo(TossError::class.java)
                        if (error?.code == ALREADY_PROCESSED_PAYMENT) {
                            PaymentApproval.AlreadyProcessed
                        } else {
                            PaymentApproval.Rejected(error?.code ?: "UNKNOWN", error?.message ?: "결제사가 승인을 거절했습니다.")
                        }
                    }
                    else -> error("토스 결제 승인 실패: ${response.statusCode}")
                }
            }

    override fun find(paymentKey: String): Payment? =
        restClient.get().uri("/v1/payments/{paymentKey}", paymentKey)
            .exchange { _, response ->
                when {
                    response.statusCode.is2xxSuccessful -> response.bodyTo(TossPayment::class.java).toPayment()
                    // 없는 결제 키(NOT_FOUND_PAYMENT)이거나 형식이 틀린 키다. 키 설정 오류(401·403)나 한도 초과(429)는 오류로 둔다.
                    response.statusCode == HttpStatus.NOT_FOUND || response.statusCode == HttpStatus.BAD_REQUEST -> null
                    else -> error("토스 결제 조회 실패: ${response.statusCode}")
                }
            }

    private fun TossPayment?.toPayment(): Payment {
        checkNotNull(this) { "토스 결제 응답 본문이 비어 있음" }
        return Payment(paymentKey, orderId, paid = status == PAID, totalAmount)
    }

    private data class TossPayment(val paymentKey: String, val orderId: String, val status: String, val totalAmount: Long)

    private data class TossError(val code: String, val message: String)

    companion object {
        private val CONNECT_TIMEOUT = Duration.ofSeconds(3)

        /** 토스는 승인 응답을 오래 기다리라고 안내한다(카드사 응답 지연). */
        private val READ_TIMEOUT = Duration.ofSeconds(30)
        private const val ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT"

        /** 토스 결제 상태 중 승인 완료. */
        private const val PAID = "DONE"
    }
}
