package com.maruhxn.fflux.donation.port.toss

import org.springframework.boot.context.properties.ConfigurationProperties

/** 토스페이먼츠 API. [secretKey]는 결제위젯 시크릿 키이고 환경변수(`TOSS_SECRET_KEY`)로만 받는다. */
@ConfigurationProperties("fflux.toss")
data class TossPaymentsProperties(
    val apiUrl: String = "https://api.tosspayments.com",
    val secretKey: String = "",
) {
    override fun toString() = "TossPaymentsProperties(apiUrl=$apiUrl, secretKey=***)"
}
