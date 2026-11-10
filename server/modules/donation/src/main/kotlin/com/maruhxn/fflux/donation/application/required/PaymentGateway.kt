package com.maruhxn.fflux.donation.application.required

/** 결제사(토스페이먼츠) 결제 승인·조회. 결제사가 응답하지 못하면 예외를 던진다. */
interface PaymentGateway {
    /** 결제창에서 인증된 결제를 [amount]원으로 승인한다. */
    fun confirm(paymentKey: String, orderId: String, amount: Long): PaymentApproval

    /** 결제를 조회한다. 결제사에 그런 결제가 없거나 결제 키 형식이 틀리면 null. */
    fun find(paymentKey: String): Payment?
}

sealed interface PaymentApproval {
    data class Approved(val payment: Payment) : PaymentApproval

    /** 이미 승인된 결제다(앞선 승인 응답이 유실됐거나 다른 요청이 먼저 승인했다). 조회로 확인한다. */
    data object AlreadyProcessed : PaymentApproval

    /** 결제사가 승인을 거절했다. [message]는 사용자에게 보여도 되는 결제사 안내다. */
    data class Rejected(val code: String, val message: String) : PaymentApproval
}

/** [paid]는 결제사가 결제를 승인 완료했는지다. */
data class Payment(
    val paymentKey: String,
    val orderId: String,
    val paid: Boolean,
    val totalAmount: Long,
)
