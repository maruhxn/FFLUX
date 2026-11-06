package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.common.event.EventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class SpringEventPublisherAdapter(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : EventPublisherPort {

    override fun publish(event: Any) {
        applicationEventPublisher.publishEvent(event)
    }
}
