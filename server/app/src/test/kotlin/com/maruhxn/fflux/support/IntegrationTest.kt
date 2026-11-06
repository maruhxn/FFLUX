package com.maruhxn.fflux.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient

/**
 * 앱 전체를 띄워 HTTP 경계에서 검증하는 통합 테스트의 기반.
 * 응답 상태와 상관없이 본문을 확인할 수 있도록 [http]는 4xx·5xx에서 예외를 던지지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    protected lateinit var http: RestClient

    @BeforeEach
    fun setUpIntegrationTest() {
        databaseCleaner.clean()
        http = RestClient.builder()
            .baseUrl("http://localhost:$port")
            .defaultStatusHandler({ true }) { _, _ -> }
            .build()
    }
}
