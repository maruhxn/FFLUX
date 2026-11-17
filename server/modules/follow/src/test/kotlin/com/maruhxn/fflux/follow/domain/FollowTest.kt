package com.maruhxn.fflux.follow.domain

import com.maruhxn.fflux.common.error.ConflictException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals

class FollowTest {

    private val now = LocalDateTime.of(2026, 11, 17, 12, 0)

    @Test
    fun `다른 회원을 팔로우한다`() {
        val follow = Follow.follow(followerId = 1, streamerId = 2, now = now)

        assertEquals(listOf(1L, 2L, now), listOf(follow.followerId, follow.streamerId, follow.createdAt))
    }

    @Test
    fun `자기 자신을 팔로우하면 409`() {
        assertThrows<ConflictException> { Follow.follow(followerId = 1, streamerId = 1, now = now) }
    }
}
