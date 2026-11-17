package com.maruhxn.fflux.follow.port.web

import com.maruhxn.fflux.common.auth.LoginMember
import com.maruhxn.fflux.common.auth.PublicApi
import com.maruhxn.fflux.follow.application.provided.FollowFinder
import com.maruhxn.fflux.follow.application.provided.FollowModifier
import com.maruhxn.fflux.follow.application.provided.FollowStatusResult
import com.maruhxn.fflux.follow.application.provided.FollowingResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 스트리머 팔로우. 팔로우 상태 조회만 비로그인을 허용한다. */
@RestController
@RequestMapping("/api")
class FollowController(
    private val followModifier: FollowModifier,
    private val followFinder: FollowFinder,
) {
    @PublicApi
    @GetMapping("/streamers/{streamerId}/follow")
    fun status(member: LoginMember?, @PathVariable streamerId: Long): FollowStatusResult =
        followFinder.findStatus(streamerId, member?.memberId)

    @PutMapping("/streamers/{streamerId}/follow")
    fun follow(member: LoginMember, @PathVariable streamerId: Long): ResponseEntity<Unit> {
        followModifier.follow(member.memberId, streamerId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/streamers/{streamerId}/follow")
    fun unfollow(member: LoginMember, @PathVariable streamerId: Long): ResponseEntity<Unit> {
        followModifier.unfollow(member.memberId, streamerId)
        return ResponseEntity.noContent().build()
    }

    /** 로그인한 회원 자신이 팔로우한 스트리머. */
    @GetMapping("/me/followings")
    fun followings(member: LoginMember): List<FollowingResult> = followFinder.findFollowings(member.memberId)
}
