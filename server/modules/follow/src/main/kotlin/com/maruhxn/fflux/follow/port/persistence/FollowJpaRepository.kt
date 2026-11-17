package com.maruhxn.fflux.follow.port.persistence

import com.maruhxn.fflux.follow.application.required.FollowRepository
import com.maruhxn.fflux.follow.domain.Follow
import org.springframework.data.jpa.repository.JpaRepository

interface FollowJpaRepository : JpaRepository<Follow, Long>, FollowRepository
