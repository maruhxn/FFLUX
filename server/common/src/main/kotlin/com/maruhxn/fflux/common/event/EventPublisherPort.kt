package com.maruhxn.fflux.common.event

/**
 * 모듈 간 비동기 전파 통로. 이벤트는 원시 타입과 ID만 담는다.
 * 받는 쪽은 `@TransactionalEventListener(AFTER_COMMIT)`로 받는다.
 */
interface EventPublisherPort {
    fun publish(event: Any)
}
