package com.maruhxn.fflux.donation.domain

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 회원이 방송을 통해 그 방송의 스트리머에게 보내는 후원 한 건. 결제 전에 PENDING으로 만들고, 결제가 승인되면 DONE이 된다.
 * 후원 대상([streamerId])은 서버가 조회한 방송의 스트리머이고, 후원자 닉네임과 메시지는 요청 시점의 값을 남긴다.
 * DONE·CANCELED는 한 번만 바뀌는 값이라 조건부 UPDATE(`WHERE status = 'PENDING'`)로 쓴다.
 */
@Entity
@Table(name = "donation")
class Donation protected constructor(
    orderId: String,
    broadcastId: Long,
    streamerId: Long,
    donorId: Long,
    donorNickname: String,
    amount: DonationAmount,
    message: DonationMessage?,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /** 결제사에 넘기는 주문 ID. 유니크하다. */
    @Column(nullable = false, unique = true, length = 64)
    val orderId: String = orderId

    @Column(nullable = false)
    val broadcastId: Long = broadcastId

    @Column(nullable = false)
    val streamerId: Long = streamerId

    @Column(nullable = false)
    val donorId: Long = donorId

    @Column(nullable = false)
    val donorNickname: String = donorNickname

    @Embedded
    val amount: DonationAmount = amount

    /** 없으면 null. 요청 때만 받고 확정 단계에서는 바꾸지 않는다. */
    @Embedded
    val message: DonationMessage? = message

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    var status: DonationStatus = DonationStatus.PENDING
        protected set

    /** 결제사가 준 결제 키. DONE이 되어야 생긴다. */
    var paymentKey: String? = null
        protected set

    @Column(nullable = false)
    val createdAt: LocalDateTime = createdAt

    /** DONE이 된 시각. */
    var confirmedAt: LocalDateTime? = null
        protected set

    fun checkDonor(memberId: Long) {
        if (memberId != donorId) throw ForbiddenException("후원한 회원만 할 수 있습니다.")
    }

    /**
     * [amount] 결제를 승인해 확정해야 하는지. 이미 DONE이면 false(확정할 것이 없다).
     * CANCELED면 409, 결제 금액이 요청 금액과 다르면 400.
     */
    fun needsConfirm(amount: Long): Boolean {
        if (status == DonationStatus.CANCELED) throw ConflictException("취소된 후원입니다.")
        if (status == DonationStatus.DONE) return false
        if (amount != this.amount.value) throw BadRequestException("결제 금액이 후원 요청과 다릅니다.", field = "amount")
        return true
    }

    /** 결제사가 승인한 결제가 이 후원의 것(같은 주문, 같은 금액)인지 본다. 아니면 409. */
    fun checkPayment(orderId: String, paid: Boolean, totalAmount: Long) {
        if (!isPaidBy(orderId, paid, totalAmount)) throw ConflictException("결제 정보가 후원 요청과 다릅니다.")
    }

    /** 결제사의 결제가 이 후원을 승인 완료한 결제(같은 주문, 같은 금액)인지. */
    fun isPaidBy(orderId: String, paid: Boolean, totalAmount: Long): Boolean =
        orderId == this.orderId && paid && totalAmount == amount.value

    companion object {
        /** 자기 방송이면 403, 후원할 수 없는 상태([donatable]이 false)의 방송이면 409. */
        fun request(
            orderId: String,
            broadcastId: Long,
            streamerId: Long,
            donatable: Boolean,
            donorId: Long,
            donorNickname: String,
            amount: DonationAmount,
            message: DonationMessage?,
            now: LocalDateTime,
        ): Donation {
            if (donorId == streamerId) throw ForbiddenException("자기 방송에는 후원할 수 없습니다.")
            if (!donatable) throw ConflictException("방송 중이거나 끝난 방송에만 후원할 수 있습니다.")
            return Donation(orderId, broadcastId, streamerId, donorId, donorNickname, amount, message, now)
        }
    }
}
