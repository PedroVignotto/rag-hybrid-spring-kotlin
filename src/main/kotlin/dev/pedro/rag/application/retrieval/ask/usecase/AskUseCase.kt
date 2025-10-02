package dev.pedro.rag.application.retrieval.ask.usecase

import dev.pedro.rag.application.retrieval.ask.dto.AskInput
import dev.pedro.rag.application.retrieval.ask.dto.AskOutput

interface AskUseCase {
    fun handle(input: AskInput): AskOutput
}
