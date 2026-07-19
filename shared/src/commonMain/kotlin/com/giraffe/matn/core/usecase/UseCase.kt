package com.giraffe.matn.core.usecase

import com.giraffe.matn.core.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Shared contract for one-shot use cases (Constitution Principle III).
 *
 * The caller (a [com.giraffe.matn.presentation.base.BaseViewModel]) owns the coroutine
 * scope; the use case never launches its own.
 */
interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): Resource<R>
}

/**
 * Shared contract for observable use cases that expose a cold [Flow] of data (e.g. reads
 * backed by SQLDelight's reactive queries). Failures inside observation surface as an
 * empty/last-good emission or are mapped by the ViewModel; repositories already drop
 * corrupt rows (Phase 0) rather than throwing.
 */
interface FlowUseCase<in P, out R> {
    operator fun invoke(params: P): Flow<R>
}