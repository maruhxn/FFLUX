package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.required.ViewerCounter
import com.maruhxn.fflux.realtime.application.provided.ViewerCountFinder
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * 방송 목록·상세의 시청자 수를 realtime 모듈에 맡긴다.
 * realtime 허브도 구독할 때 broadcast 모듈로 스트리머를 찾으므로, 빈 생성 순환을 [Lazy]로 끊는다.
 */
@Component
class ViewerCountAdapter(
    @Lazy private val viewerCountFinder: ViewerCountFinder,
) : ViewerCounter {
    override fun viewerCounts(broadcastIds: Collection<Long>): Map<Long, Int> = viewerCountFinder.viewerCounts(broadcastIds)
}
