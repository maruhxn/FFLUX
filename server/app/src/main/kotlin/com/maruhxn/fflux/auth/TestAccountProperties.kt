package com.maruhxn.fflux.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 평가용 테스트 계정 설정. 비밀번호는 저장소가 아니라 환경변수로만 받고, 비어 있는 계정은 시드하지 않는다.
 * 평가자가 바로 로그인해야 하므로 기본으로 켜져 있다(`TEST_ACCOUNTS_ENABLED=false`로 끈다).
 */
@ConfigurationProperties("fflux.test-accounts")
class TestAccountProperties(
    val enabled: Boolean = true,
    private val streamerPassword: String = "",
    private val viewerPassword: String = "",
) {
    fun passwordOf(account: TestAccount): String = when (account) {
        TestAccount.STREAMER -> streamerPassword
        TestAccount.VIEWER -> viewerPassword
    }

    // 비밀번호가 로그나 설정 출력에 남지 않게 한다.
    override fun toString(): String = "TestAccountProperties(enabled=$enabled)"
}

enum class TestAccount(val username: String, val nickname: String) {
    STREAMER("streamer", "테스트스트리머"),
    VIEWER("viewer", "테스트시청자"),
    ;

    companion object {
        fun ofUsername(username: String): TestAccount? = entries.find { it.username == username }
    }
}
