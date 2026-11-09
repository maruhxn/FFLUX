package com.maruhxn.fflux.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 테스트가 시각을 앞으로 옮길 수 있는 시계. 통합 테스트마다 현재 시각으로 되돌린다.
 * MySQL `DATETIME(6)`과 같은 마이크로초 단위로 자른다. Linux JVM은 나노초까지 주는데 MySQL은 7번째 자리를 반올림해 저장하므로,
 * 자르지 않으면 저장한 시각과 테스트가 기대한 시각이 1µs씩 어긋난다(macOS는 마이크로초까지만 줘서 드러나지 않는다).
 */
class TestClock : Clock() {
    private var instant: Instant = now()

    fun advance(duration: Duration) {
        instant = instant.plus(duration)
    }

    fun reset() {
        instant = now()
    }

    override fun getZone(): ZoneId = SEOUL

    /** 시간대는 서울 하나로 고정이라 그대로 돌려준다. 그래야 [advance]를 따라간다. */
    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = instant

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

    @Bean
    @Primary
    fun testClock(): TestClock = TestClock()
}
