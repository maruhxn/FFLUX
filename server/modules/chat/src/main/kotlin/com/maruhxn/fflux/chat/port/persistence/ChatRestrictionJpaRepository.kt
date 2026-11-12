package com.maruhxn.fflux.chat.port.persistence

import com.maruhxn.fflux.chat.application.required.ChatRestrictionRepository
import com.maruhxn.fflux.chat.domain.ChatRestriction
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRestrictionJpaRepository : JpaRepository<ChatRestriction, Long>, ChatRestrictionRepository
