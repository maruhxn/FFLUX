package com.maruhxn.fflux.realtime.application.required

/** 스트리머의 팔로워를 찾는다(구현은 `app` 어댑터). */
interface FollowerFinder {
    fun followerIdsOf(streamerId: Long): List<Long>
}
