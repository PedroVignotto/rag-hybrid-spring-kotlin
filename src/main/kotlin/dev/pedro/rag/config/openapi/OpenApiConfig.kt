package dev.pedro.rag.config.openapi

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.servers.Server
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@OpenAPIDefinition(
    info =
        Info(
            title = "RAG Hybrid — API",
            version = "v1",
            description =
                "Provider-agnostic API for LLM chat (sync/stream) and retrieval. " +
                    "Includes endpoints for chat, document ingestion, and search with stable, versioned schemas.",
        ),
    servers = [Server(url = "/", description = "Local")],
)
@Configuration
class OpenApiConfig {
    @Bean
    fun baseOpenApi(): OpenAPI = OpenAPI().components(Components())
}
