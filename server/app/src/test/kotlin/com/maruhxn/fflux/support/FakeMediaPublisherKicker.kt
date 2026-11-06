package com.maruhxn.fflux.support

import com.maruhxn.fflux.broadcast.application.required.MediaPublisherKicker
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/** 미디어 서버 대신 kick 호출을 기록한다. [failing]이면 실패를 흉내 낸다. 통합 테스트마다 비운다. */
class FakeMediaPublisherKicker : MediaPublisherKicker {
    val kickedPaths: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var failing = false

    override fun kickPublisher(mediaPath: String) {
        kickedPaths += mediaPath
        if (failing) error("kick 실패(테스트)")
    }

    fun reset() {
        kickedPaths.clear()
        failing = false
    }
}

@TestConfiguration(proxyBeanMethods = false)
class FakeMediaPublisherKickerConfiguration {

    @Bean
    @Primary
    fun fakeMediaPublisherKicker(): FakeMediaPublisherKicker = FakeMediaPublisherKicker()
}
