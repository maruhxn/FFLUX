package com.maruhxn.fflux.chat.port.persistence

import com.maruhxn.fflux.chat.application.required.ChatRepository
import com.maruhxn.fflux.chat.domain.Chat
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ChatJpaRepository : JpaRepository<Chat, Long>, ChatRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Chat c WHERE c.id = :id")
    override fun findForUpdateById(id: Long): Chat?
}
