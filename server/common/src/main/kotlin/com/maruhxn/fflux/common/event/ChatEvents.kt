package com.maruhxn.fflux.common.event

/** LIVE 방송에 채팅이 올라왔다. [authorNickname]은 작성 시점의 닉네임이다. */
data class ChatPosted(val broadcastId: Long, val chatId: Long, val authorNickname: String, val content: String)
