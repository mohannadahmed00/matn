package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.StorageUsage
import com.giraffe.matn.domain.repository.DownloadedContentRepository
import kotlinx.coroutines.flow.Flow

/**
 * T062 (FR-025/FR-026/FR-030) — streams the Settings tab's aggregate storage usage. Pure
 * delegation to [DownloadedContentRepository.observeStorageUsage] — all ordering and totalling logic
 * lives in the repository (content-delivery-contract.md §4).
 */
@org.koin.core.annotation.Factory
class ObserveStorageUsageUseCase(
    private val repository: DownloadedContentRepository,
) : FlowUseCase<Unit, StorageUsage> {
    override fun invoke(params: Unit): Flow<StorageUsage> = repository.observeStorageUsage()
}
