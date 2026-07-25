package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.repository.VerseRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams the full verse list of a matn in matn-global `displayNumber` order (FR-006/FR-007).
 * Verse `arabicText` is passed through **verbatim** — no normalization, trimming, or
 * transformation — so diacritics stay byte-identical end-to-end (FR-008/SC-002).
 */
@org.koin.core.annotation.Factory
class ObserveVersesUseCase(
    private val repo: VerseRepository,
) : FlowUseCase<String, List<Verse>> {
    override fun invoke(params: String): Flow<List<Verse>> = repo.observeVerses(params)
}