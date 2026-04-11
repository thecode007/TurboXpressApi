package com.thecode007.turboxpress

import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@EnableScheduling
@SpringBootApplication
class TurboXpressApplication

fun main(args: Array<String>) {
    runApplication<TurboXpressApplication>(*args)
}
