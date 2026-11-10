package com.maruhxn.fflux.chat.domain

import com.maruhxn.fflux.common.error.ConflictException
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** LIVE 방송에 보낸 채팅. 고치거나 지울 수 없다. 작성자 닉네임은 작성 시점의 값을 남긴다. */
@Entity
@Table(name = "chat")
class Chat protected constructor(
    broadcastId: Long,
    authorId: Long,
    authorNickname: String,
    content: ChatContent,
    createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val broadcastId: Long = broadcastId

    @Column(nullable = false)
    val authorId: Long = authorId

    @Column(nullable = false)
    val authorNickname: String = authorNickname

    @Embedded
    val content: ChatContent = content

    @Column(nullable = false)
    val createdAt: LocalDateTime = createdAt

    companion object {
        /** [live]는 방송이 지금 LIVE인지다. LIVE 방송에만 채팅할 수 있다(아니면 409). */
        fun post(broadcastId: Long, live: Boolean, authorId: Long, authorNickname: String, content: ChatContent, now: LocalDateTime): Chat {
            if (!live) throw ConflictException("방송 중에만 채팅할 수 있습니다.")
            return Chat(broadcastId, authorId, authorNickname, content, now)
        }
    }
}
