package com.maruhxn.fflux.support

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.concurrent.thread

/** 받은 SSE 이벤트 하나. 주석(heartbeat 등)은 담지 않는다. */
data class SseEvent(val name: String, val data: String)

/**
 * 테스트용 SSE 구독. 응답 헤더를 받은 뒤 본문을 줄 단위로 읽어 이벤트로 모은다.
 * 서버가 구독을 등록한 뒤 응답을 시작하므로, [open]이 돌아온 다음에 일어난 일은 이벤트로 받는다.
 */
class SseSubscription private constructor(
    val status: Int,
    private val lines: Stream<String>,
) : AutoCloseable {

    private val events = LinkedBlockingQueue<SseEvent>()

    init {
        thread(isDaemon = true) {
            var name = "message"
            val data = StringBuilder()
            runCatching {
                lines.forEach { line ->
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) events.put(SseEvent(name, data.toString()))
                            name = "message"
                            data.clear()
                        }
                        line.startsWith("event:") -> name = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> data.append(line.removePrefix("data:"))
                    }
                }
            }
        }
    }

    /** 다음 이벤트를 [timeout] 안에 받지 못하면 실패한다. */
    fun next(timeout: Duration = Duration.ofSeconds(5)): SseEvent =
        checkNotNull(events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)) { "SSE 이벤트가 ${timeout}안에 오지 않았습니다." }

    /** [within] 동안 이벤트가 오지 않았는지. */
    fun nothingWithin(within: Duration = Duration.ofMillis(500)): Boolean =
        events.poll(within.toMillis(), TimeUnit.MILLISECONDS) == null

    override fun close() = lines.close()

    companion object {
        private val client = HttpClient.newHttpClient()

        fun open(url: String, cookie: String? = null): SseSubscription {
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "text/event-stream")
                .apply { if (cookie != null) header("Cookie", cookie) }
                .timeout(Duration.ofSeconds(5))
                .GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofLines())
            return SseSubscription(response.statusCode(), response.body())
        }
    }
}
