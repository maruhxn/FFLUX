package com.maruhxn.fflux.broadcast.application.required

/** 미디어 서버에서 송출 세션을 끊는 포트. 종료 요청이 커밋된 뒤에만 부른다. */
interface MediaPublisherKicker {
    /** [mediaPath]로 송출 중인 세션을 끊는다. 송출 중이 아니면 아무것도 하지 않는다. 실패하면 예외를 던진다. */
    fun kickPublisher(mediaPath: String)
}
