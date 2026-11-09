package com.maruhxn.fflux.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * API를 다른 출처에서 부르는 프론트 주소(운영은 Vercel의 `https://fflux.kro.kr`).
 * 비어 있으면 같은 출처만 받는다(로컬은 Vite 프록시로 같은 출처다).
 */
@ConfigurationProperties("fflux.cors")
class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
