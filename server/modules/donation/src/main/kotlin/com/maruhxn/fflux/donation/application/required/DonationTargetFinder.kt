package com.maruhxn.fflux.donation.application.required

/** 후원할 방송의 스트리머와 후원 가능 여부를 본다(구현은 `app` 어댑터). */
interface DonationTargetFinder {
    /** 없거나 삭제된 방송이면 404(`NotFoundException`)를 던진다. */
    fun find(broadcastId: Long): DonationTarget
}

/** [donatable]은 방송이 LIVE 또는 ENDED인지다. */
data class DonationTarget(
    val streamerId: Long,
    val donatable: Boolean,
)
