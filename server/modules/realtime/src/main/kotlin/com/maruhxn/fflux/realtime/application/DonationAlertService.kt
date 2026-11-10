package com.maruhxn.fflux.realtime.application

import com.maruhxn.fflux.realtime.application.provided.BroadcastEventSender
import com.maruhxn.fflux.realtime.application.provided.DonationAlertSender
import com.maruhxn.fflux.realtime.application.required.LiveBroadcastFinder
import org.springframework.stereotype.Service

/** LIVE 방송의 후원만 `donation` 이벤트로 보낸다. 끝난 방송의 후원은 내역에만 남는다. */
@Service
class DonationAlertService(
    private val liveBroadcastFinder: LiveBroadcastFinder,
    private val broadcastEventSender: BroadcastEventSender,
) : DonationAlertSender {

    override fun send(broadcastId: Long, donorNickname: String, amount: Long, message: String?) {
        if (!liveBroadcastFinder.isLive(broadcastId)) return
        broadcastEventSender.send(broadcastId, "donation", DonationMessage(donorNickname, amount, message))
    }

    private data class DonationMessage(val donorNickname: String, val amount: Long, val message: String?)
}
