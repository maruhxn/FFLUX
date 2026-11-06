package com.maruhxn.fflux.broadcast.application.provided

/** 스트리머의 방송 등록·수정·삭제. */
interface ManageBroadcast {
    /** READY 방송을 만들고 방송 ID를 돌려준다. */
    fun register(command: RegisterBroadcastCommand): Long

    /** 없거나 삭제됐으면 404, 스트리머가 아니면 403, ENDED면 409. */
    fun edit(command: EditBroadcastCommand)

    /** soft delete. 없거나 삭제됐으면 404, 스트리머가 아니면 403, LIVE면 409. */
    fun delete(broadcastId: Long, memberId: Long)
}

data class RegisterBroadcastCommand(
    val streamerId: Long,
    val streamerNickname: String,
    val title: String,
    val description: String?,
)

data class EditBroadcastCommand(
    val broadcastId: Long,
    val memberId: Long,
    val title: String,
    val description: String?,
)
