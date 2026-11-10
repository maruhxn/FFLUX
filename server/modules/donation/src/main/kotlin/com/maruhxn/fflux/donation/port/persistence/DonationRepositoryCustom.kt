package com.maruhxn.fflux.donation.port.persistence

import com.maruhxn.fflux.donation.domain.Donation

interface DonationRepositoryCustom {
    fun findDonePage(broadcastId: Long, cursor: Long?, limit: Int): List<Donation>
}
