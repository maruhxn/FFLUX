package com.maruhxn.fflux.support

import com.maruhxn.fflux.donation.application.required.Payment
import com.maruhxn.fflux.donation.application.required.PaymentApproval
import com.maruhxn.fflux.donation.application.required.PaymentGateway
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 토스 대신 결제를 승인·조회한다. 토스처럼 같은 결제 키를 두 번 승인하면 `ALREADY_PROCESSED_PAYMENT`다.
 * [rejecting]이면 승인을 거절한다. 통합 테스트마다 비운다.
 */
class FakePaymentGateway : PaymentGateway {
    private val payments = ConcurrentHashMap<String, Payment>()
    val confirmed: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var rejecting = false

    override fun confirm(paymentKey: String, orderId: String, amount: Long): PaymentApproval {
        confirmed += paymentKey
        if (rejecting) return PaymentApproval.Rejected("REJECT_CARD_COMPANY", "카드사에서 거절했습니다.")
        val payment = Payment(paymentKey, orderId, paid = true, amount)
        return if (payments.putIfAbsent(paymentKey, payment) == null) PaymentApproval.Approved(payment) else PaymentApproval.AlreadyProcessed
    }

    override fun find(paymentKey: String): Payment = payments[paymentKey] ?: error("없는 결제(테스트): $paymentKey")

    /** 우리 서버가 응답을 받지 못한 채 토스에서 이미 승인된 결제를 만든다. */
    fun approvedElsewhere(paymentKey: String, orderId: String, amount: Long) {
        payments[paymentKey] = Payment(paymentKey, orderId, paid = true, amount)
    }

    fun reset() {
        payments.clear()
        confirmed.clear()
        rejecting = false
    }
}

@TestConfiguration(proxyBeanMethods = false)
class FakePaymentGatewayConfiguration {

    @Bean
    @Primary
    fun fakePaymentGateway(): FakePaymentGateway = FakePaymentGateway()
}
