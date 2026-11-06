package com.maruhxn.fflux.member.domain

import com.maruhxn.fflux.common.error.BadRequestException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class MemberValueTest {

    @Test
    fun `아이디는 영문 소문자 숫자 _ 4~20자다`() {
        assertEquals("new_bie01", Username.of("new_bie01").value)
        listOf("abc", "a".repeat(21), "Newbie", "new bie", " newbie").forEach { raw ->
            val e = assertFailsWith<BadRequestException>(raw) { Username.of(raw) }
            assertEquals(setOf("username"), e.errors.keys)
        }
    }

    @Test
    fun `닉네임은 앞뒤 공백을 지운 뒤 2~12자다`() {
        assertEquals("새내기", Nickname.of("  새내기  ").value)
        assertEquals(12, Nickname.of(" ${"가".repeat(12)} ").value.length)
        listOf("가", " 가 ", "가".repeat(13)).forEach { raw ->
            val e = assertFailsWith<BadRequestException>(raw) { Nickname.of(raw) }
            assertEquals(setOf("nickname"), e.errors.keys)
        }
    }

    @Test
    fun `비밀번호는 8~64자다`() {
        assertEquals("password", Password.of("password").value)
        listOf("short", "a".repeat(65)).forEach { raw ->
            val e = assertFailsWith<BadRequestException> { Password.of(raw) }
            assertEquals(setOf("password"), e.errors.keys)
        }
    }

    @Test
    fun `비밀번호는 문자열로 바꿔도 드러나지 않는다`() {
        assertFalse(Password.of("secret-password").toString().contains("secret"))
    }

    @Test
    fun `가입하면 아이디와 닉네임과 암호화한 비밀번호를 담는다`() {
        val member = Member.register(Username.of("newbie01"), "{bcrypt}hash", Nickname.of("새내기"), LocalDateTime.of(2026, 11, 6, 21, 0))

        assertEquals(Username.of("newbie01"), member.username)
        assertEquals(Nickname.of("새내기"), member.nickname)
        assertEquals("{bcrypt}hash", member.encodedPassword)
    }
}
