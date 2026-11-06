package com.maruhxn.fflux.broadcast.port.mediamtx

import com.maruhxn.fflux.broadcast.application.required.MediaPublisherKicker
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/** MediaMTX Control API로 송출 세션을 끊는다: path의 송출 세션 ID를 읽고, 그 WebRTC 세션을 kick한다. */
@Component
class MediaMtxClient(properties: MediaMtxProperties) : MediaPublisherKicker {

    private val restClient = RestClient.builder()
        .baseUrl(properties.apiUrl)
        // 종료 API 응답을 오래 붙잡지 않도록 짧게 끊는다.
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(TIMEOUT)
                setReadTimeout(TIMEOUT)
            },
        )
        .build()

    override fun kickPublisher(mediaPath: String) {
        val path = restClient.get()
            .uri { it.path("/v3/paths/get/").path(mediaPath).build() }
            .exchange { _, response ->
                when {
                    // path가 없으면 이미 송출이 끊긴 것이다.
                    response.statusCode == HttpStatus.NOT_FOUND -> null
                    response.statusCode.isError -> error("MediaMTX path 조회 실패: ${response.statusCode}")
                    else -> response.bodyTo(MediaMtxPath::class.java)
                }
            }
        val source = path?.source ?: return
        check(source.type == WEBRTC_SESSION) { "WebRTC가 아닌 송출 세션: ${source.type}" }
        restClient.post().uri("/v3/webrtc/sessions/kick/{id}", source.id).retrieve().toBodilessEntity()
    }

    private data class MediaMtxPath(val source: Source?)

    private data class Source(val type: String, val id: String)

    companion object {
        private val TIMEOUT = Duration.ofSeconds(2)
        private const val WEBRTC_SESSION = "webRTCSession"
    }
}
