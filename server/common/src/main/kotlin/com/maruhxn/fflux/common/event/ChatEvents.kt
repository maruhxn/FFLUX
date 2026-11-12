package com.maruhxn.fflux.common.event

import java.time.LocalDateTime

/** LIVE 방송에 채팅이 올라왔다. [authorNickname]은 작성 시점의 닉네임이다. */
data class ChatPosted(val broadcastId: Long, val chatId: Long, val authorId: Long, val authorNickname: String, val content: String)

/** 스트리머가 방송의 채팅 하나를 가렸다. */
data class ChatHidden(val broadcastId: Long, val chatId: Long)

/**
 * 스트리머가 회원을 채팅 금지했거나([restricted]) 풀었다. [broadcastId]는 그 스트리머의 LIVE 방송이다.
 * [until]은 금지가 끝나는 시각이고, 풀 때까지 금지이거나 해제면 null이다.
 */
data class ChatRestrictionChanged(val broadcastId: Long, val memberId: Long, val restricted: Boolean, val until: LocalDateTime?)
