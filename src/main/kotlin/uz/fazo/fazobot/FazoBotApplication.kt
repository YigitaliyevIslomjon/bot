package uz.fazo.fazobot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FazoBotApplication

fun main(args: Array<String>) {
    runApplication<FazoBotApplication>(*args)
}
