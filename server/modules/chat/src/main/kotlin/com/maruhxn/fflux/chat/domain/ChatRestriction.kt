package com.maruhxn.fflux.chat.domain

import com.maruhxn.fflux.common.error.ConflictException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import com.maruhxn.fflux.common.error.ForbiddenException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 채팅 금지: 스트리머가 한 회원을 자기 방송들에서 채팅하지 못하게 막는다. 스트리머·회원 한 쌍에 하나이고, 다시 금지하면 덮어쓴다.
 * [until]이 null이면 스트리머가 풀 때까지다. 기간이 지난 금지는 금지가 아니다. [memberNickname]은 금지 목록에 보여 줄 금지 당시 닉네임이다.
 */
@Entity
@Table(name = "chat_restriction")
class ChatRestriction protected constructor(
    streamerId: Long,
    memberId: Long,
    memberNickname: String,
    until: LocalDateTime?,
    restrictedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val streamerId: Long = streamerId

    @Column(nullable = false)
    val memberId: Long = memberId

    @Column(nullable = false, length = 12)
    var memberNickname: String = memberNickname
        protected set

    var until: LocalDateTime? = until
        protected set

    /** 마지막으로 금지한 시각. 다시 금지하면 바뀐다. 금지 목록은 이 순서로 보여 준다. */
    @Column(nullable = false)
    var restrictedAt: LocalDateTime = restrictedAt
        protected set

    /** [now]에 아직 금지 중인지. */
    fun isActive(now: LocalDateTime): Boolean = until?.isAfter(now) ?: true

    /** 이미 금지한 회원을 다시 금지한다. 마지막 선택으로 덮어쓴다. */
    fun restrictAgain(memberNickname: String, duration: ChatRestrictionDuration, now: LocalDateTime) {
        this.memberNickname = memberNickname
        until = untilOf(duration, now)
        restrictedAt = now
    }

    /** [now]에 금지 중이면 금지된 회원의 채팅 작성을 403으로 거절한다. 메시지에 언제까지인지 담는다. */
    fun checkCanChat(now: LocalDateTime) {
        if (!isActive(now)) return
        val until = until ?: throw ForbiddenException("스트리머가 풀 때까지 채팅이 금지되었습니다.")
        throw ForbiddenException("${until.format(UNTIL_FORMAT)}까지 채팅이 금지되었습니다.")
    }

    companion object {
        private val UNTIL_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm:ss")

        /** 스트리머가 회원을 금지한다. 자기 자신은 금지할 수 없다(409). */
        fun restrict(
            streamerId: Long,
            memberId: Long,
            memberNickname: String,
            duration: ChatRestrictionDuration,
            now: LocalDateTime,
        ): ChatRestriction {
            if (streamerId == memberId) throw ConflictException("자기 자신은 채팅 금지할 수 없습니다.")
            return ChatRestriction(streamerId, memberId, memberNickname, untilOf(duration, now), restrictedAt = now)
        }

        private fun untilOf(duration: ChatRestrictionDuration, now: LocalDateTime): LocalDateTime? =
            duration.length?.let { now.plus(it) }
    }
}
