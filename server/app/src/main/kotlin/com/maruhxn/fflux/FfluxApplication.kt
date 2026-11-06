package com.maruhxn.fflux

import com.maruhxn.fflux.config.ClockConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
class FfluxApplication

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone(ClockConfig.SEOUL))
    runApplication<FfluxApplication>(*args)
}
