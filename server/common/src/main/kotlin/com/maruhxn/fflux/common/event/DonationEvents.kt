package com.maruhxn.fflux.common.event

/** 후원이 확정(PENDING → DONE)됐다. 한 후원에 한 번만 발행한다. [message]는 없으면 null이다. */
data class DonationConfirmed(val broadcastId: Long, val donorNickname: String, val amount: Long, val message: String?)
