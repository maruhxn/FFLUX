package com.maruhxn.fflux.follow.application.required

/** 팔로우할 스트리머(회원)가 있는지 본다(구현은 `app` 어댑터). */
interface FollowTargetFinder {
    fun exists(memberId: Long): Boolean
}
