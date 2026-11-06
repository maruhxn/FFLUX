package com.maruhxn.fflux.realtime.application.provided

/** 방송 구독자 모두에게 SSE 이벤트를 보낸다. [data]는 JSON으로 보낸다. 구독자가 없으면 아무것도 하지 않는다. */
interface BroadcastEventSender {
    fun send(broadcastId: Long, name: String, data: Any)
}
