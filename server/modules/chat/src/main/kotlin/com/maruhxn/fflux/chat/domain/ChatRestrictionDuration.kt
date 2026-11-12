package com.maruhxn.fflux.chat.domain

import java.time.Duration

/** 채팅 금지 기간. [length]가 null이면 스트리머가 풀 때까지다. */
enum class ChatRestrictionDuration(val length: Duration?) {
    THIRTY_SECONDS(Duration.ofSeconds(30)),
    ONE_HOUR(Duration.ofHours(1)),
    UNTIL_LIFTED(null),
}
