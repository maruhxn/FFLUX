package com.maruhxn.fflux.donation.application.provided

/** 후원 요청과 결제 결과 반영. 확정·취소는 후원한 회원만 한다. */
interface DonationProcessor {
    /**
     * 결제 전 PENDING 후원을 만든다. 없거나 삭제된 방송은 404, 자기 방송은 403, LIVE·ENDED가 아닌 방송은 409.
     * 결제창에 넘길 주문 ID와 금액을 돌려준다.
     */
    fun request(command: RequestDonationCommand): DonationResult

    /**
     * 결제창 승인 뒤 결제사 승인 API로 결제를 확정해 DONE으로 만든다. 이미 DONE이면 그대로 성공한다.
     * 없으면 404, 후원자가 아니면 403, 금액이 요청과 다르면 400, 취소됐거나 결제가 승인되지 않으면 409.
     */
    fun confirm(command: ConfirmDonationCommand): DonationResult

    /** 결제창에서 실패·취소해 돌아오면 CANCELED로 만든다. 이미 CANCELED면 그대로 성공한다. 없으면 404, 후원자가 아니면 403, DONE이면 409. */
    fun cancel(orderId: String, memberId: Long): DonationResult
}

/** [donorNickname]은 요청 시점의 닉네임으로 남긴다. [message]는 없거나 공백만이면 메시지 없음이다. */
data class RequestDonationCommand(
    val broadcastId: Long,
    val donorId: Long,
    val donorNickname: String,
    val amount: Long,
    val message: String?,
)

/** [paymentKey]와 [amount]는 결제창이 successUrl로 넘긴 값이다. */
data class ConfirmDonationCommand(
    val orderId: String,
    val memberId: Long,
    val paymentKey: String,
    val amount: Long,
)

data class DonationResult(
    val orderId: String,
    val broadcastId: Long,
    val amount: Long,
)
