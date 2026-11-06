package com.maruhxn.fflux.broadcast.domain

import com.maruhxn.fflux.common.error.BadRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BroadcastValueTest {

    @Test
    fun `제목은 앞뒤 공백을 지운 값이다`() {
        assertEquals("첫 방송", BroadcastTitle("  첫 방송  ").value)
    }

    @Test
    fun `제목 길이는 공백을 지운 뒤 50자까지다`() {
        assertEquals(50, BroadcastTitle("  ${"가".repeat(50)}  ").value.length)
        assertFailsWith<BadRequestException> { BroadcastTitle("가".repeat(51)) }
    }

    @Test
    fun `공백뿐인 제목은 만들 수 없다`() {
        assertFailsWith<BadRequestException> { BroadcastTitle("   ") }
    }

    @Test
    fun `같은 글자의 제목은 같은 값이다`() {
        assertEquals(BroadcastTitle("첫 방송"), BroadcastTitle(" 첫 방송"))
    }

    @Test
    fun `비었거나 공백뿐인 설명은 설명이 없는 것이다`() {
        assertNull(BroadcastDescription.of(null))
        assertNull(BroadcastDescription.of("   "))
    }

    @Test
    fun `설명은 공백을 지운 뒤 500자까지다`() {
        assertEquals("설명", BroadcastDescription.of(" 설명 ")?.value)
        assertEquals(500, BroadcastDescription.of(" ${"가".repeat(500)} ")?.value?.length)
        assertFailsWith<BadRequestException> { BroadcastDescription.of("가".repeat(501)) }
    }
}
