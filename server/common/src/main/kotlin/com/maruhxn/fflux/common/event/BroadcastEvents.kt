package com.maruhxn.fflux.common.event

/** 방송 상태가 실제로 바뀌었다. [status]는 바뀐 뒤 상태(`LIVE`, `ENDED`)다. */
data class BroadcastStatusChanged(val broadcastId: Long, val status: String)

/** 스트리머가 방송 정보(제목, 설명)를 고쳤다. 값은 고친 뒤 값이다. */
data class BroadcastUpdated(val broadcastId: Long, val title: String, val description: String?)
