package com.maruhxn.fflux.chat.domain

import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** LIVE 방송에 보낸 채팅. 작성자는 고치거나 지울 수 없고, 방송의 스트리머가 가릴 수 있다. 작성자 닉네임은 작성 시점의 값을 남긴다. */
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

    /** 스트리머가 가린 시각. 가리지 않았으면 null. */
    var hiddenAt: LocalDateTime? = null
        protected set

    /**
     * 채팅 금지: 이 채팅을 쓴 회원을 금지한다. [memberId]가 방송의 스트리머([streamerId])여야 하고(아니면 403),
     * 작성자가 스트리머 자신이면 409. 이미 금지했으면([current]) 마지막 선택으로 덮어써 같은 금지를 돌려준다.
     */
    fun restrictAuthor(
        memberId: Long,
        streamerId: Long,
        current: ChatRestriction?,
        duration: ChatRestrictionDuration,
        now: LocalDateTime,
    ): ChatRestriction {
        if (memberId != streamerId) throw ForbiddenException("방송의 스트리머만 채팅 금지할 수 있습니다.")
        return current?.apply { restrictAgain(authorNickname, duration, now) }
            ?: ChatRestriction.restrict(streamerId, authorId, authorNickname, duration, now)
    }

    /**
     * 채팅 가리기: [memberId]가 방송의 스트리머([streamerId])여야 한다(아니면 403).
     * 가렸으면 true, 이미 가려져 있으면 그대로 두고 false.
     */
    fun hide(memberId: Long, streamerId: Long, now: LocalDateTime): Boolean {
        if (memberId != streamerId) throw ForbiddenException("방송의 스트리머만 채팅을 가릴 수 있습니다.")
        if (hiddenAt != null) return false
        hiddenAt = now
        return true
    }

    companion object {
        /**
         * [live]는 방송이 지금 LIVE인지, [restriction]은 그 방송 스트리머가 작성자에게 건 채팅 금지다.
         * 금지 중이면 403, LIVE가 아니면 409 순서로 거절한다.
         */
        fun post(
            broadcastId: Long,
            live: Boolean,
            restriction: ChatRestriction?,
            authorId: Long,
            authorNickname: String,
            content: ChatContent,
            now: LocalDateTime,
        ): Chat {
            restriction?.checkCanChat(now)
            if (!live) throw ConflictException("방송 중에만 채팅할 수 있습니다.")
            return Chat(broadcastId, authorId, authorNickname, content, now)
        }
    }
}
