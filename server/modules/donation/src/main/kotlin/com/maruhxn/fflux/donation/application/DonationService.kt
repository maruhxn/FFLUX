package com.maruhxn.fflux.donation.application

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.common.event.DonationConfirmed
import com.maruhxn.fflux.common.event.EventPublisherPort
import com.maruhxn.fflux.donation.application.provided.ConfirmDonationCommand
import com.maruhxn.fflux.donation.application.provided.DonationFinder
import com.maruhxn.fflux.donation.application.provided.DonationHistoryItem
import com.maruhxn.fflux.donation.application.provided.DonationPage
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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

/**
 * 확정·취소는 결제사 호출을 트랜잭션 밖에서 하고, 상태는 저장소의 조건부 UPDATE 한 번으로 바꾼다.
 * 같은 결제를 동시에 확정해도 DONE은 한 번만 되고, DONE으로 바꾼 쪽만 `DonationConfirmed`를 발행한다.
 */
@Service
class DonationService(
    private val donationRepository: DonationRepository,
    private val donationTargetFinder: DonationTargetFinder,
    private val paymentGateway: PaymentGateway,
    private val eventPublisherPort: EventPublisherPort,
    private val clock: Clock,
) : DonationProcessor, DonationFinder {

    private val log = LoggerFactory.getLogger(javaClass)

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
        if (!markDone(donation, payment)) throw ConflictException("취소된 후원입니다.")
        return donation.toResult()
    }

    override fun confirmByWebhook(paymentKey: String) {
        val payment = paymentGateway.find(paymentKey) ?: return log.warn("토스 웹훅: 결제사에 없는 결제")
        val donation = donationRepository.findByOrderId(payment.orderId)
            ?: return log.warn("토스 웹훅: 모르는 주문 orderId={}", payment.orderId)
        if (!donation.isPaidBy(payment.orderId, payment.paid, payment.totalAmount)) {
            return log.info("토스 웹훅: 승인 완료가 아니거나 후원과 다른 결제 orderId={}", payment.orderId)
        }
        if (!markDone(donation, payment)) log.warn("토스 웹훅: 취소된 후원에 승인된 결제 orderId={}", payment.orderId)
    }

    override fun cancel(orderId: String, memberId: Long): DonationResult {
        val donation = findOrThrow(orderId)
        donation.checkDonor(memberId)
        if (donationRepository.markCanceled(orderId) == 0 && findOrThrow(orderId).status == DonationStatus.DONE) {
            throw ConflictException("이미 확정된 후원은 취소할 수 없습니다.")
        }
        return donation.toResult()
    }

    /** 한 개를 더 읽어 다음 페이지가 있는지 판단한다. */
    @Transactional(readOnly = true)
    override fun findPage(broadcastId: Long, cursor: Long?): DonationPage {
        val rows = donationRepository.findDonePage(broadcastId, cursor, PAGE_SIZE + 1)
        val donations = rows.take(PAGE_SIZE)
        return DonationPage(
            items = donations.map { it.toHistoryItem() },
            nextCursor = if (rows.size > PAGE_SIZE) donations.last().id else null,
            totalAmount = donationRepository.sumDoneAmount(broadcastId),
        )
    }

    /**
     * successUrl 확정과 웹훅이 함께 쓰는 확정 경로. 조건부 UPDATE로 PENDING만 DONE으로 바꾸고, 바꿨으면 확정 이벤트를 발행한다.
     * 0행이면 다른 확정이 먼저 DONE으로 바꿨거나(성공, 이벤트는 그쪽이 발행) 그사이 취소됐다(false).
     */
    private fun markDone(donation: Donation, payment: Payment): Boolean {
        if (donationRepository.markDone(payment.orderId, payment.paymentKey, LocalDateTime.now(clock)) == 1) {
            eventPublisherPort.publish(
                DonationConfirmed(donation.broadcastId, donation.donorNickname, donation.amount.value, donation.message?.value),
            )
            return true
        }
        return findOrThrow(payment.orderId).status == DonationStatus.DONE
    }

    /** 이미 승인된 결제(승인 응답 유실, 동시 확정)는 조회 결과로 판단한다. */
    private fun approve(paymentKey: String, donation: Donation): Payment =
        when (val approval = paymentGateway.confirm(paymentKey, donation.orderId, donation.amount.value)) {
            is PaymentApproval.Approved -> approval.payment
            PaymentApproval.AlreadyProcessed ->
                paymentGateway.find(paymentKey) ?: throw ConflictException("결제사에서 결제를 찾을 수 없습니다.")
            is PaymentApproval.Rejected -> throw ConflictException("결제를 승인하지 못했습니다: ${approval.message}")
        }

    private fun findOrThrow(orderId: String): Donation =
        donationRepository.findByOrderId(orderId) ?: throw NotFoundException("후원을 찾을 수 없습니다.")

    private fun Donation.toResult() = DonationResult(orderId, broadcastId, amount.value)

    private fun Donation.toHistoryItem() = DonationHistoryItem(
        id = id,
        donorNickname = donorNickname,
        amount = amount.value,
        message = message?.value,
        confirmedAt = checkNotNull(confirmedAt) { "DONE 후원에 확정 시각이 없습니다. id=$id" },
    )

    companion object {
        const val PAGE_SIZE = 20
    }
}
