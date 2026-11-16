package com.maruhxn.fflux.follow.application.provided

interface FollowFinder {
    /** 스트리머의 팔로워 수와, 회원([memberId])이 팔로우 중인지. 비로그인([memberId]가 null)이면 팔로우 중이 아니다. */
    fun findStatus(streamerId: Long, memberId: Long?): FollowStatusResult

    /** 스트리머를 팔로우하는 회원 ID 전부. */
    fun findFollowerIds(streamerId: Long): List<Long>
}

data class FollowStatusResult(val followerCount: Long, val following: Boolean)
