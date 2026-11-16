package com.maruhxn.fflux.follow.application.provided

/** 회원의 팔로우·언팔로우. 둘 다 이미 그 상태면 그대로 성공한다. */
interface FollowModifier {
    /** 없는 회원이면 404, 자기 자신이면 409. */
    fun follow(memberId: Long, streamerId: Long)

    fun unfollow(memberId: Long, streamerId: Long)
}
