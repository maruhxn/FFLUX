package com.maruhxn.fflux.broadcast.port.mediamtx

import org.springframework.boot.context.properties.ConfigurationProperties

/** MediaMTX Control API 주소. Docker 내부망에서만 열린다(로컬은 127.0.0.1에만 publish). */
@ConfigurationProperties("fflux.mediamtx")
data class MediaMtxProperties(
    val apiUrl: String = "http://localhost:9997",
)
