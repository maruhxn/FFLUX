package com.maruhxn.fflux.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.system(SEOUL)

    companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
