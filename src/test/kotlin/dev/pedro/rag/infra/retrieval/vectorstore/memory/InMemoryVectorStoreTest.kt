package dev.pedro.rag.infra.retrieval.vectorstore.memory

import dev.pedro.rag.application.retrieval.ports.VectorStoreContractTest
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.domain.retrieval.CollectionSpec

class InMemoryVectorStoreTest : VectorStoreContractTest() {
    override fun sut(): VectorStorePort = InMemoryVectorStore()

    override fun collection(): CollectionSpec = CollectionSpec(provider = "test", model = "fake", dim = 3)
}
