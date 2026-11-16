package com.maruhxn.fflux.follow.application.required

import com.maruhxn.fflux.follow.domain.Follow

interface FollowRepository {
    fun save(follow: Follow): Follow

    fun existsByFollowerIdAndStreamerId(followerId: Long, streamerId: Long): Boolean

    /** 지운 행 수. */
    fun deleteByFollowerIdAndStreamerId(followerId: Long, streamerId: Long): Long

    fun countByStreamerId(streamerId: Long): Long

    fun findAllByStreamerId(streamerId: Long): List<Follow>
}
