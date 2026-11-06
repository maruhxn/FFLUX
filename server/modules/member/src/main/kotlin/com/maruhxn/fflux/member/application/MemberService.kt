package com.maruhxn.fflux.member.application

import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.UnauthorizedException
import com.maruhxn.fflux.member.application.provided.AuthenticateMember
import com.maruhxn.fflux.member.application.provided.FindMember
import com.maruhxn.fflux.member.application.provided.MemberSummary
import com.maruhxn.fflux.member.application.provided.RegisterMember
import com.maruhxn.fflux.member.application.provided.RegisterMemberCommand
import com.maruhxn.fflux.member.application.required.MemberRepository
import com.maruhxn.fflux.member.domain.Member
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
        if (memberRepository.existsByUsername(command.username)) {
            throw ConflictException("이미 사용 중인 아이디입니다.")
        }
        val member = Member(
            username = command.username,
            encodedPassword = checkNotNull(passwordEncoder.encode(command.password)),
            nickname = command.nickname,
            createdAt = LocalDateTime.now(clock),
        )
        return memberRepository.save(member).id
    }

    @Transactional(readOnly = true)
    override fun authenticate(username: String, password: String): MemberSummary {
        val member = memberRepository.findByUsername(username)
        // 없는 아이디도 해시 비교를 한 번 해서, 응답 시간으로 아이디 존재 여부가 드러나지 않게 한다.
        val matches = passwordEncoder.matches(password, member?.encodedPassword ?: dummyHash)
        if (member == null || !matches) {
            throw UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        return member.toSummary()
    }

    @Transactional(readOnly = true)
    override fun findByUsername(username: String): MemberSummary? =
        memberRepository.findByUsername(username)?.toSummary()

    private val dummyHash: String by lazy { checkNotNull(passwordEncoder.encode("dummy-password-for-timing")) }

    private fun Member.toSummary() = MemberSummary(memberId = id, nickname = nickname)
}
