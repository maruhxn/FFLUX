package com.maruhxn.fflux.support

import com.maruhxn.fflux.realtime.application.BroadcastEventHub
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

/**
 * 앱 전체를 띄워 HTTP 경계에서 검증하는 통합 테스트의 기반.
 * 응답 상태와 상관없이 본문을 확인할 수 있도록 [http]는 4xx·5xx에서 예외를 던지지 않는다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "fflux.test-accounts.enabled=true",
        "fflux.test-accounts.streamer-password=$TEST_STREAMER_PASSWORD",
        "fflux.test-accounts.viewer-password=$TEST_VIEWER_PASSWORD",
        "fflux.cors.allowed-origins=$TEST_FRONT_ORIGIN",
        // 상태 맞추기는 테스트가 직접 부른다. 주기 실행이 다른 테스트의 LIVE 방송을 끝내지 않게 끈다.
        "fflux.mediamtx.reconcile.enabled=false",
    ],
)
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class, FakeMediaPublisherKickerConfiguration::class, FakeMediaPublishingFinderConfiguration::class, FakePaymentGatewayConfiguration::class)
abstract class IntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    protected lateinit var clock: TestClock

    @Autowired
    protected lateinit var kicker: FakeMediaPublisherKicker

    @Autowired
    protected lateinit var hub: BroadcastEventHub

    @Autowired
    protected lateinit var paymentGateway: FakePaymentGateway

    protected lateinit var http: RestClient

    @BeforeEach
    fun setUpIntegrationTest() {
        databaseCleaner.clean()
        clock.reset()
        kicker.reset()
        paymentGateway.reset()
        // 방송 ID가 테이블을 비울 때마다 다시 시작하므로, 앞 테스트에서 끊긴 구독이 같은 ID에 남아 시청자 수에 섞이지 않게 비운다.
        hub.completeAll()
        http = RestClient.builder()
            .baseUrl("http://localhost:$port")
            .defaultStatusHandler({ true }) { _, _ -> }
            .build()
    }

    /** [path]의 SSE를 구독한다. 다 쓰면 닫는다. */
    protected fun openSse(path: String, cookie: String? = null): SseSubscription =
        SseSubscription.open("http://localhost:$port$path", cookie)

    /** 응답의 Set-Cookie에서 세션 쿠키(`SESSION=...`)를 꺼낸다. */
    protected fun sessionCookieOf(headers: HttpHeaders): String =
        headers[HttpHeaders.SET_COOKIE].orEmpty()
            .first { it.startsWith("SESSION=") }
            .substringBefore(';')
}

const val TEST_STREAMER_PASSWORD = "streamer-test-pw"
const val TEST_VIEWER_PASSWORD = "viewer-test-pw"
const val TEST_FRONT_ORIGIN = "https://front.example"
