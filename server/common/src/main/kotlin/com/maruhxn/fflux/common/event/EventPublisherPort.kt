package com.maruhxn.fflux.common.event

/**
 * 이벤트 발행 통로. 모듈 간 비동기 전파(이벤트는 원시 타입과 ID만 담는다)와, 모듈 안의 커밋 뒤 처리(예: 종료 요청 뒤 송출 세션 kick)에 쓴다.
 * 받는 쪽은 `@TransactionalEventListener(AFTER_COMMIT)`로 받는다.
 */
interface EventPublisherPort {
    fun publish(event: Any)
}
