package com.maruhxn.fflux

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FfluxApplication

fun main(args: Array<String>) {
    runApplication<FfluxApplication>(*args)
}
