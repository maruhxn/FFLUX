package com.maruhxn.fflux

import com.maruhxn.fflux.config.ClockConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.TimeZone

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class FfluxApplication

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone(ClockConfig.SEOUL))
    runApplication<FfluxApplication>(*args)
}
