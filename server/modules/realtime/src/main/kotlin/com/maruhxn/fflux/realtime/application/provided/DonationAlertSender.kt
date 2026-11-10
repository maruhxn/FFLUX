package com.maruhxn.fflux.realtime.application.provided

/** 확정된 후원을 그 방송 구독자에게 알린다. 방송이 LIVE일 때만 보낸다. */
interface DonationAlertSender {
    fun send(broadcastId: Long, donorNickname: String, amount: Long, message: String?)
}
