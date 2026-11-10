package com.maruhxn.fflux.donation.application.provided

import java.time.LocalDateTime

/** 방송별 후원 내역. 확정(DONE)된 후원만 담는다. */
interface DonationFinder {
    /** 방송의 확정된 후원을 최신순으로 읽는다. [cursor]는 앞 페이지의 `nextCursor`다. 방송이 삭제돼도 기록은 남아 있다. */
    fun findPage(broadcastId: Long, cursor: Long?): DonationPage
}

/** [donorNickname]은 후원 요청 때의 닉네임이다. [message]는 없으면 null. */
data class DonationHistoryItem(
    val id: Long,
    val donorNickname: String,
    val amount: Long,
    val message: String?,
    val confirmedAt: LocalDateTime,
)

/** [totalAmount]는 페이지와 상관없이 방송의 확정된 후원 금액 합계다. [nextCursor]가 null이면 마지막 페이지다. */
data class DonationPage(
    val items: List<DonationHistoryItem>,
    val nextCursor: Long?,
    val totalAmount: Long,
)
