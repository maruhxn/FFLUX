package com.maruhxn.fflux.donation.application

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.donation.application.provided.ConfirmDonationCommand
import com.maruhxn.fflux.donation.application.provided.DonationProcessor
import com.maruhxn.fflux.donation.application.provided.DonationResult
import com.maruhxn.fflux.donation.application.provided.RequestDonationCommand
import com.maruhxn.fflux.donation.application.required.DonationRepository
import com.maruhxn.fflux.donation.application.required.DonationTargetFinder
import com.maruhxn.fflux.donation.application.required.Payment
import com.maruhxn.fflux.donation.application.required.PaymentApproval
import com.maruhxn.fflux.donation.application.required.PaymentGateway
import com.maruhxn.fflux.donation.domain.Donation
import com.maruhxn.fflux.donation.domain.DonationAmount
import com.maruhxn.fflux.donation.domain.DonationMessage
import com.maruhxn.fflux.donation.domain.DonationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

/**
 * 확정·취소는 결제사 호출을 트랜잭션 밖에서 하고, 상태는 저장소의 조건부 UPDATE 한 번으로 바꾼다.
 * 같은 결제를 동시에 확정해도 DONE은 한 번만 된다.
 */
@Service
class DonationService(
    private val donationRepository: DonationRepository,
    private val donationTargetFinder: DonationTargetFinder,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock,
) : DonationProcessor {

    @Transactional
    override fun request(command: RequestDonationCommand): DonationResult {
        val amount = runCatching { DonationAmount.of(command.amount) }
        val message = runCatching { DonationMessage.of(command.message) }
        BadRequestException.throwIfAny(listOf(amount, message))
        val target = donationTargetFinder.find(command.broadcastId)
        val donation = Donation.request(
            orderId = UUID.randomUUID().toString(),
            broadcastId = command.broadcastId,
            streamerId = target.streamerId,
            donatable = target.donatable,
            donorId = command.donorId,
            donorNickname = command.donorNickname,
            amount = amount.getOrThrow(),
            message = message.getOrThrow(),
            now = LocalDateTime.now(clock),
        )
        return donationRepository.save(donation).toResult()
    }

    override fun confirm(command: ConfirmDonationCommand): DonationResult {
        val donation = findOrThrow(command.orderId)
        donation.checkDonor(command.memberId)
        if (!donation.needsConfirm(command.amount)) return donation.toResult()

        val payment = approve(command.paymentKey, donation)
        donation.checkPayment(payment.orderId, payment.paid, payment.totalAmount)
        // 0행이면 다른 확정 요청이 먼저 DONE으로 바꿨거나, 그사이 취소됐다.
        if (donationRepository.markDone(donation.orderId, payment.paymentKey, LocalDateTime.now(clock)) == 0 &&
            findOrThrow(donation.orderId).status != DonationStatus.DONE
        ) {
            throw ConflictException("취소된 후원입니다.")
        }
        return donation.toResult()
    }

    override fun cancel(orderId: String, memberId: Long): DonationResult {
        val donation = findOrThrow(orderId)
        donation.checkDonor(memberId)
        if (donationRepository.markCanceled(orderId) == 0 && findOrThrow(orderId).status == DonationStatus.DONE) {
            throw ConflictException("이미 확정된 후원은 취소할 수 없습니다.")
        }
        return donation.toResult()
    }

    /** 이미 승인된 결제(승인 응답 유실, 동시 확정)는 조회 결과로 판단한다. */
    private fun approve(paymentKey: String, donation: Donation): Payment =
        when (val approval = paymentGateway.confirm(paymentKey, donation.orderId, donation.amount.value)) {
            is PaymentApproval.Approved -> approval.payment
            PaymentApproval.AlreadyProcessed -> paymentGateway.find(paymentKey)
            is PaymentApproval.Rejected -> throw ConflictException("결제를 승인하지 못했습니다: ${approval.message}")
        }

    private fun findOrThrow(orderId: String): Donation =
        donationRepository.findByOrderId(orderId) ?: throw NotFoundException("후원을 찾을 수 없습니다.")

    private fun Donation.toResult() = DonationResult(orderId, broadcastId, amount.value)
}
