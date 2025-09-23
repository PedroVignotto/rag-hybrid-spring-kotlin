package dev.pedro.rag

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class RagHybridSpringKotlinApplication

fun main(args: Array<String>) {
    runApplication<RagHybridSpringKotlinApplication>(*args)
}
