package com.maruhxn.fflux.follow.application.required

/** 팔로우할 스트리머(회원)를 다른 모듈에서 본다(구현은 `app` 어댑터). */
interface FollowTargetFinder {
    fun exists(memberId: Long): Boolean

    /** 있는 회원만 담는다. */
    fun nicknamesOf(memberIds: Collection<Long>): Map<Long, String>

    /** 스트리머의 LIVE 방송 ID. 방송 중이 아니면 null. */
    fun liveBroadcastIdOf(streamerId: Long): Long?
}
