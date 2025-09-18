package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.delete.usecase.DefaultDeleteUseCase
import dev.pedro.rag.application.retrieval.delete.usecase.DeleteUseCase
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.domain.retrieval.CollectionSpec
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class RetrievalDeleteConfig {
    @Bean("deleteUseCaseCore")
    fun deleteUseCaseCore(
        vectorStorePort: VectorStorePort,
        collectionSpec: CollectionSpec,
    ): DeleteUseCase =
        DefaultDeleteUseCase(
            vectorStore = vectorStorePort,
            activeCollection = collectionSpec,
        )

    @Bean
    @Primary
    fun deleteUseCase(
        @Qualifier("deleteUseCaseCore") core: DeleteUseCase,
    ): DeleteUseCase = core
}
