package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.follow.application.required.FollowTargetFinder
import com.maruhxn.fflux.member.application.provided.MemberFinder
import org.springframework.stereotype.Component

/** follow 모듈이 팔로우할 회원과 그 닉네임은 member 모듈에서, 방송 중인지는 broadcast 모듈에서 본다. */
@Component
class FollowTargetAdapter(
    private val memberFinder: MemberFinder,
    private val broadcastFinder: BroadcastFinder,
) : FollowTargetFinder {
    override fun exists(memberId: Long): Boolean = memberFinder.exists(memberId)

    override fun nicknamesOf(memberIds: Collection<Long>): Map<Long, String> = memberFinder.findNicknames(memberIds)

    override fun liveBroadcastIdOf(streamerId: Long): Long? = broadcastFinder.findLiveIdOf(streamerId)
}
