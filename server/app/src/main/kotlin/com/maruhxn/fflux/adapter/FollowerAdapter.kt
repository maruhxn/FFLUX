package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.follow.application.provided.FollowFinder
import com.maruhxn.fflux.realtime.application.required.FollowerFinder
import org.springframework.stereotype.Component

/** realtime이 방송 시작을 알릴 팔로워를 follow 모듈에서 찾는다. */
@Component
class FollowerAdapter(
    private val followFinder: FollowFinder,
) : FollowerFinder {
    override fun followerIdsOf(streamerId: Long): List<Long> = followFinder.findFollowerIds(streamerId)
}
