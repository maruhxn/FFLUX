package com.maruhxn.fflux.donation.port.web

import com.maruhxn.fflux.common.auth.LoginMember
import com.maruhxn.fflux.common.auth.PublicApi
import com.maruhxn.fflux.donation.application.provided.DonationFinder
import com.maruhxn.fflux.donation.application.provided.DonationPage
import com.maruhxn.fflux.donation.application.provided.ConfirmDonationCommand
import com.maruhxn.fflux.donation.application.provided.DonationProcessor
import com.maruhxn.fflux.donation.application.provided.DonationResult
import com.maruhxn.fflux.donation.application.provided.RequestDonationCommand
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 후원은 방송 아래에서 요청하고 내역을 읽으며(`/api/broadcasts/{id}/donations`), 결제창에서 돌아오면 주문 ID로 확정하거나 취소한다
 * (`/api/donations/{orderId}/confirm`, `/cancel`).
 */
@RestController
@RequestMapping("/api")
class DonationController(
    private val donationProcessor: DonationProcessor,
    private val donationFinder: DonationFinder,
) {
    @PublicApi
    @GetMapping("/broadcasts/{broadcastId}/donations")
    fun list(@PathVariable broadcastId: Long, @RequestParam(required = false) cursor: Long?): DonationPage =
        donationFinder.findPage(broadcastId, cursor)

    @PostMapping("/broadcasts/{broadcastId}/donations")
    fun request(member: LoginMember, @PathVariable broadcastId: Long, @RequestBody request: DonationRequest): ResponseEntity<DonationResult> {
        val result = donationProcessor.request(
            RequestDonationCommand(broadcastId, member.memberId, member.nickname, request.amount, request.message),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @PostMapping("/donations/{orderId}/confirm")
    fun confirm(member: LoginMember, @PathVariable orderId: String, @RequestBody request: ConfirmDonationRequest): DonationResult =
        donationProcessor.confirm(ConfirmDonationCommand(orderId, member.memberId, request.paymentKey, request.amount))

    @PostMapping("/donations/{orderId}/cancel")
    fun cancel(member: LoginMember, @PathVariable orderId: String): DonationResult =
        donationProcessor.cancel(orderId, member.memberId)
}

/** 금액 범위와 메시지 길이는 도메인 값 객체가 검사한다. */
data class DonationRequest(val amount: Long, val message: String? = null)

/** 결제창이 successUrl로 넘긴 결제 키와 금액. */
data class ConfirmDonationRequest(val paymentKey: String, val amount: Long)
