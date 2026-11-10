package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.donation.application.required.DonationTarget
import com.maruhxn.fflux.donation.application.required.DonationTargetFinder
import org.springframework.stereotype.Component

/** donation 모듈이 후원할 방송의 스트리머와 상태를 broadcast 모듈에서 본다. */
@Component
class DonationTargetAdapter(
    private val broadcastFinder: BroadcastFinder,
) : DonationTargetFinder {
    /** 없거나 삭제된 방송이면 상세 조회가 404를 던진다. 시청 경로는 LIVE 방송에만 있다. */
    override fun find(broadcastId: Long): DonationTarget {
        val detail = broadcastFinder.find(broadcastId, memberId = null)
        val donatable = detail.watchPath != null || broadcastFinder.findEnded(listOf(broadcastId)).isNotEmpty()
        return DonationTarget(detail.broadcast.streamerId, donatable)
    }
}
