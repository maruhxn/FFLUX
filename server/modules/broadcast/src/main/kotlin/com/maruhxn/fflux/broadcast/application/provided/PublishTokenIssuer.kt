package com.maruhxn.fflux.broadcast.application.provided

/** 스트리머가 방송을 송출하기 위한 송출 토큰 발급. */
interface PublishTokenIssuer {
    /**
     * 녹화 여부를 저장하고 이전 미소비 토큰을 무효화한 뒤 새 토큰을 발급한다.
     * 없거나 삭제됐으면 404, 스트리머가 아니면 403, READY가 아니거나, 토큰을 이미 한 번 썼거나, 스트리머에게 다른 LIVE 방송이 있으면 409.
     */
    fun issue(command: IssuePublishTokenCommand): PublishTokenResult
}

data class IssuePublishTokenCommand(
    val broadcastId: Long,
    val memberId: Long,
    val record: Boolean,
)

/** [token]은 원본이라 로그에 남지 않도록 [toString]에서 가린다. */
data class PublishTokenResult(
    val token: String,
    val mediaPath: String,
) {
    override fun toString() = "PublishTokenResult(token=****, mediaPath=$mediaPath)"
}
