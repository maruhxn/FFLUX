package com.maruhxn.fflux.broadcast.port.mediamtx

import com.maruhxn.fflux.broadcast.application.required.MediaPublisherKicker
import com.maruhxn.fflux.broadcast.application.required.MediaPublishing
import com.maruhxn.fflux.broadcast.application.required.MediaPublishingFinder
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * MediaMTX Control API로 송출 세션을 끊고(path의 송출 세션 ID를 읽어 그 WebRTC 세션을 kick), 송출을 받고 있는 path 목록을 읽는다.
 */
@Component
class MediaMtxClient(
    properties: MediaMtxProperties,
    private val clock: Clock,
) : MediaPublisherKicker, MediaPublishingFinder {

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

    /** `available`은 `runOnAvailable` 훅과 같은 기준(트랙을 받고 있음)이다. 연결 중이라 아직 아닌 path는 뺀다. */
    override fun findPublishings(): List<MediaPublishing> {
        val list = restClient.get()
            .uri { it.path("/v3/paths/list").queryParam("itemsPerPage", MAX_PATHS).build() }
            .retrieve()
            .body(MediaMtxPathList::class.java)
            ?: error("MediaMTX path 목록 응답이 비어 있음")
        // 목록이 잘리면 빠진 LIVE 방송을 송출 끊김으로 잘못 끝낸다. 잘렸으면 실패로 보고 이번 맞추기를 건너뛰게 한다.
        check(list.pageCount <= 1) { "MediaMTX path가 한 페이지(${MAX_PATHS}개)를 넘음: pageCount=${list.pageCount}" }
        return list.items.filter { it.available }.map { path ->
            val availableAt = path.availableTime?.let { LocalDateTime.ofInstant(it, clock.zone) } ?: LocalDateTime.now(clock)
            // DB DATETIME(6)에 맞춰 마이크로초까지만 둔다.
            MediaPublishing(path.name, availableAt.truncatedTo(ChronoUnit.MICROS))
        }
    }

    private data class MediaMtxPath(val source: Source?)

    private data class MediaMtxPathList(val pageCount: Int, val items: List<PathItem>)

    private data class PathItem(val name: String, val available: Boolean, val availableTime: Instant?)

    private data class Source(val type: String, val id: String)

    companion object {
        private val TIMEOUT = Duration.ofSeconds(2)
        private const val WEBRTC_SESSION = "webRTCSession"

        /** 방송마다 path 하나라 한 페이지에 다 들어온다. 넘치면 맞추기를 건너뛴다. */
        private const val MAX_PATHS = 1000
    }
}
