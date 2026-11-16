package com.maruhxn.fflux.follow.application

import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.follow.application.provided.FollowFinder
import com.maruhxn.fflux.follow.application.provided.FollowModifier
import com.maruhxn.fflux.follow.application.provided.FollowStatusResult
import com.maruhxn.fflux.follow.application.provided.FollowingResult
import com.maruhxn.fflux.follow.application.required.FollowRepository
import com.maruhxn.fflux.follow.application.required.FollowTargetFinder
import com.maruhxn.fflux.follow.domain.Follow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional
class FollowService(
    private val followRepository: FollowRepository,
    private val followTargetFinder: FollowTargetFinder,
    private val clock: Clock,
) : FollowModifier, FollowFinder {

    /** 같은 회원이 동시에 두 번 팔로우하면 유니크 제약에 걸려 한쪽이 409를 받는다. 결과 상태는 같다. */
    override fun follow(memberId: Long, streamerId: Long) {
        if (!followTargetFinder.exists(streamerId)) throw NotFoundException("회원을 찾을 수 없습니다.")
        val follow = Follow.follow(memberId, streamerId, LocalDateTime.now(clock))
        if (followRepository.existsByFollowerIdAndStreamerId(memberId, streamerId)) return
        followRepository.save(follow)
    }

    override fun unfollow(memberId: Long, streamerId: Long) {
        followRepository.deleteByFollowerIdAndStreamerId(memberId, streamerId)
    }

    @Transactional(readOnly = true)
    override fun findStatus(streamerId: Long, memberId: Long?): FollowStatusResult = FollowStatusResult(
        followerCount = followRepository.countByStreamerId(streamerId),
        following = memberId != null && followRepository.existsByFollowerIdAndStreamerId(memberId, streamerId),
    )

    @Transactional(readOnly = true)
    override fun findFollowerIds(streamerId: Long): List<Long> =
        followRepository.findAllByStreamerId(streamerId).map { it.followerId }

    /** 방송 중 여부는 스트리머마다 한 번씩 본다. 팔로우 수가 적다는 전제다. */
    @Transactional(readOnly = true)
    override fun findFollowings(memberId: Long): List<FollowingResult> {
        val streamerIds = followRepository.findAllByFollowerIdOrderByIdDesc(memberId).map { it.streamerId }
        val nicknames = followTargetFinder.nicknamesOf(streamerIds)
        return streamerIds
            .mapNotNull { id -> nicknames[id]?.let { FollowingResult(id, it, followTargetFinder.liveBroadcastIdOf(id)) } }
            .sortedBy { it.liveBroadcastId == null }
    }
}
