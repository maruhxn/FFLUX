package com.maruhxn.fflux.realtime.application

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 키(방송, 회원)별 SSE 구독자를 메모리에 들고 있는다(서버 1대 전제). 연결이 끊긴 구독자는 보내기에 실패할 때 빠진다.
 * [T]는 구독자마다 붙여 두는 값이다(방송 구독의 시청자 회원 ID 등).
 */
internal class SseSubscribers<K : Any, T> {

    private val subscribers = ConcurrentHashMap<K, MutableSet<Subscriber<T>>>()

    val keys: Set<K> get() = subscribers.keys

    /** 구독을 넣고 응답을 바로 시작한다. [initialEvents]는 넣은 뒤에 만들어 이 구독자에게만 보낸다. */
    fun add(key: K, tag: T, initialEvents: () -> List<SseEmitter.SseEventBuilder> = ::emptyList): SseEmitter {
        val subscriber = Subscriber(SseEmitter(TIMEOUT.toMillis()), tag)
        val emitter = subscriber.emitter
        // 빈 집합을 지우는 remove와 엇갈려도 지워진 집합에 넣지 않도록 키 단위로 원자적으로 넣는다.
        subscribers.compute(key) { _, set -> (set ?: ConcurrentHashMap.newKeySet()).apply { add(subscriber) } }
        emitter.onCompletion { remove(key, subscriber) }
        emitter.onError { remove(key, subscriber) }
        // 제한 시간이 지나면 정상 종료한다. 클라이언트(EventSource)는 알아서 다시 연결한다.
        emitter.onTimeout { emitter.complete() }
        // 응답을 바로 시작해 클라이언트가 연결됐음을 알게 한다.
        trySend(key, subscriber, SseEmitter.event().comment("connected"))
        initialEvents().forEach { trySend(key, subscriber, it) }
        return emitter
    }

    fun tagsOf(key: K): List<T> = subscribers[key].orEmpty().map { it.tag }

    fun send(key: K, event: SseEmitter.SseEventBuilder) {
        subscribers[key]?.forEach { trySend(key, it, event) }
    }

    /** 쉬는 연결에도 주기적으로 써서 죽은 구독자를 찾고, 중간 네트워크(LTE NAT 등)가 쉬는 연결을 끊지 않게 한다. */
    fun heartbeat() {
        subscribers.keys.forEach { send(it, SseEmitter.event().comment("heartbeat")) }
    }

    /** 열린 SSE 응답이 graceful shutdown을 붙잡지 않도록 모든 구독을 끝낸다(클라이언트는 다시 연결한다). */
    fun completeAll() {
        subscribers.values.forEach { set -> set.forEach { it.emitter.complete() } }
        subscribers.clear()
    }

    /** 연결이 끊겼으면 보내기가 실패한다. 그 구독자를 빼고 넘어간다. */
    private fun trySend(key: K, subscriber: Subscriber<T>, event: SseEmitter.SseEventBuilder) {
        try {
            subscriber.emitter.send(event)
        } catch (e: Exception) {
            remove(key, subscriber)
        }
    }

    private fun remove(key: K, subscriber: Subscriber<T>) {
        subscribers.computeIfPresent(key) { _, set -> set.apply { remove(subscriber) }.ifEmpty { null } }
    }

    private class Subscriber<T>(val emitter: SseEmitter, val tag: T)

    companion object {
        val TIMEOUT: Duration = Duration.ofMinutes(30)
    }
}
