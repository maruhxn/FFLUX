package com.maruhxn.fflux.donation.domain

/** 결제 전 `PENDING`에서 결제가 승인되면 `DONE`, 결제창에서 실패·취소하면 `CANCELED`가 된다. 이탈한 PENDING은 그대로 남는다. */
enum class DonationStatus { PENDING, DONE, CANCELED }
