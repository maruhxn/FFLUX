package com.maruhxn.fflux.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 테스트가 시각을 앞으로 옮길 수 있는 시계. 통합 테스트마다 현재 시각으로 되돌린다. */
class TestClock : Clock() {
    private var instant: Instant = Instant.now()

    fun advance(duration: Duration) {
        instant = instant.plus(duration)
    }

    fun reset() {
        instant = Instant.now()
    }

    override fun getZone(): ZoneId = SEOUL

    /** 시간대는 서울 하나로 고정이라 그대로 돌려준다. 그래야 [advance]를 따라간다. */
    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = instant

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

    @Bean
    @Primary
    fun testClock(): TestClock = TestClock()
}
