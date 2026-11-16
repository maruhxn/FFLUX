package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.follow.application.required.FollowTargetFinder
import com.maruhxn.fflux.member.application.provided.MemberFinder
import org.springframework.stereotype.Component

/** follow 모듈이 팔로우할 회원이 있는지 member 모듈에서 본다. */
@Component
class FollowTargetAdapter(
    private val memberFinder: MemberFinder,
) : FollowTargetFinder {
    override fun exists(memberId: Long): Boolean = memberFinder.exists(memberId)
}
