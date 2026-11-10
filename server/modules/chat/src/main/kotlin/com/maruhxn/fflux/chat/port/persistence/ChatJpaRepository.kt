package com.maruhxn.fflux.chat.port.persistence

import com.maruhxn.fflux.chat.application.required.ChatRepository
import com.maruhxn.fflux.chat.domain.Chat
import org.springframework.data.jpa.repository.JpaRepository

interface ChatJpaRepository : JpaRepository<Chat, Long>, ChatRepository
