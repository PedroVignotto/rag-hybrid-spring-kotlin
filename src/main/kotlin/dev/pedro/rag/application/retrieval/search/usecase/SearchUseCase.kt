package dev.pedro.rag.application.retrieval.search.usecase

import dev.pedro.rag.application.retrieval.search.dto.SearchInput
import dev.pedro.rag.application.retrieval.search.dto.SearchOutput

interface SearchUseCase {
    fun search(input: SearchInput): SearchOutput
}
