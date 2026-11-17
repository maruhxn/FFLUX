package com.maruhxn.fflux.follow.application.provided

interface FollowFinder {
    /** 스트리머의 팔로워 수와, 회원([memberId])이 팔로우 중인지. 비로그인([memberId]가 null)이면 팔로우 중이 아니다. */
    fun findStatus(streamerId: Long, memberId: Long?): FollowStatusResult

    /** 스트리머를 팔로우하는 회원 ID 전부. */
    fun findFollowerIds(streamerId: Long): List<Long>

    /** 회원이 팔로우한 스트리머. 방송 중인 스트리머가 먼저, 그 안에서는 최근에 팔로우한 순서다. */
    fun findFollowings(memberId: Long): List<FollowingResult>
}

data class FollowStatusResult(val followerCount: Long, val following: Boolean)

/** [nickname]은 지금 닉네임이다. [liveBroadcastId]는 방송 중이 아니면 null. */
data class FollowingResult(val streamerId: Long, val nickname: String, val liveBroadcastId: Long?)
