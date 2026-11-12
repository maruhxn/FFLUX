package com.maruhxn.fflux.support

import com.maruhxn.fflux.broadcast.application.required.MediaPublishing
import com.maruhxn.fflux.broadcast.application.required.MediaPublishingFinder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/** 미디어 서버 대신 지금 송출 중인 경로를 돌려준다. [failing]이면 조회 실패를 흉내 낸다. 통합 테스트마다 비운다. */
class FakeMediaPublishingFinder : MediaPublishingFinder {
    val publishings: MutableList<MediaPublishing> = CopyOnWriteArrayList()

    @Volatile
    var failing = false

    override fun findPublishings(): List<MediaPublishing> {
        if (failing) error("미디어 서버 조회 실패(테스트)")
        return publishings.toList()
    }

    fun reset() {
        publishings.clear()
        failing = false
    }
}

@TestConfiguration(proxyBeanMethods = false)
class FakeMediaPublishingFinderConfiguration {

    @Bean
    @Primary
    fun fakeMediaPublishingFinder(): FakeMediaPublishingFinder = FakeMediaPublishingFinder()
}
