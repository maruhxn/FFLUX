package com.maruhxn.fflux.member.application

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.UnauthorizedException
import com.maruhxn.fflux.member.application.provided.AuthenticateMember
import com.maruhxn.fflux.member.application.provided.FindMember
import com.maruhxn.fflux.member.application.provided.MemberSummary
import com.maruhxn.fflux.member.application.provided.RegisterMember
import com.maruhxn.fflux.member.application.provided.RegisterMemberCommand
import com.maruhxn.fflux.member.application.required.MemberRepository
import com.maruhxn.fflux.member.domain.Member
import com.maruhxn.fflux.member.domain.Nickname
import com.maruhxn.fflux.member.domain.Password
import com.maruhxn.fflux.member.domain.Username
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
) : RegisterMember, AuthenticateMember, FindMember {

    override fun register(command: RegisterMemberCommand): Long {
        // 여러 칸이 틀리면 모든 칸의 에러를 한 번에 알린다.
        val usernameResult = runCatching { Username.of(command.username) }
        val passwordResult = runCatching { Password.of(command.password) }
        val nicknameResult = runCatching { Nickname.of(command.nickname) }
        BadRequestException.throwIfAny(listOf(usernameResult, passwordResult, nicknameResult))
        val username = usernameResult.getOrThrow()

        if (memberRepository.existsByUsername(username)) {
            throw ConflictException("이미 사용 중인 아이디입니다.")
        }
        val member = Member.register(
            username = username,
            encodedPassword = checkNotNull(passwordEncoder.encode(passwordResult.getOrThrow().value)),
            nickname = nicknameResult.getOrThrow(),
            now = LocalDateTime.now(clock),
        )
        return memberRepository.save(member).id
    }

    @Transactional(readOnly = true)
    override fun authenticate(username: String, password: String): MemberSummary {
        val member = findMember(username)
        // 없는 아이디도 해시 비교를 한 번 해서, 응답 시간으로 아이디 존재 여부가 드러나지 않게 한다.
        val matches = passwordEncoder.matches(password, member?.encodedPassword ?: dummyHash)
        if (member == null || !matches) {
            throw UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        return member.toSummary()
    }

    @Transactional(readOnly = true)
    override fun findByUsername(username: String): MemberSummary? = findMember(username)?.toSummary()

    /** 로그인·조회 입력은 형식을 검사하지 않는다. 아이디 형식에 맞지 않으면 없는 회원으로 본다(400이 아니라 401·없음). */
    private fun findMember(username: String): Member? =
        runCatching { Username.of(username) }.getOrNull()?.let(memberRepository::findByUsername)

    private val dummyHash: String by lazy { checkNotNull(passwordEncoder.encode("dummy-password-for-timing")) }

    private fun Member.toSummary() = MemberSummary(memberId = id, nickname = nickname.value)
}
