package com.maruhxn.fflux.auth

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.member.application.provided.FindMember
import com.maruhxn.fflux.member.application.provided.RegisterMember
import com.maruhxn.fflux.member.application.provided.RegisterMemberCommand
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/** 앱이 뜰 때 테스트 계정이 없으면 만든다. */
@Component
class TestAccountSeeder(
    private val properties: TestAccountProperties,
    private val findMember: FindMember,
    private val registerMember: RegisterMember,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) return
        TestAccount.entries.forEach { account ->
            val password = properties.passwordOf(account)
            when {
                password.isBlank() -> log.warn("테스트 계정 {}의 비밀번호가 없어 시드하지 않습니다.", account.username)
                findMember.findByUsername(account.username) == null -> try {
                    registerMember.register(RegisterMemberCommand(account.username, password, account.nickname))
                    log.info("테스트 계정 {}을 만들었습니다.", account.username)
                } catch (e: BadRequestException) {
                    // 비밀번호 규칙에 맞지 않는 환경변수 때문에 앱 기동이 막히지 않게 한다. 값은 남기지 않는다.
                    log.warn("테스트 계정 {}을 시드하지 않습니다: {}", account.username, e.message)
                }
            }
        }
    }
}
