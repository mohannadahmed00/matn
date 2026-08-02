package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.StudentCatalogRepository
import com.giraffe.matn.domain.delivery.DeviceStorage
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.repository.DownloadedContentRepository
import kotlinx.coroutines.flow.first

/**
 * Accepts a download request after the **ordered preconditions** in delivery-contract.md §4.
 * Renamed from `InstallMatnContentUseCase`; the starter guard is gone with the starter (FR-040).
 *
 * 1. Already downloaded, downloading or queued → success no-op (duplicate tap, FR-015).
 * 2. `freeSpaceBytes() < downloadSizeBytes` → refuse, stating **required against available**
 *    (FR-019). The size comes from the catalog overview, which is always present offline, so this
 *    check never waits on a network lookup.
 * 3. Otherwise enqueue. The repository owns ordering; exactly one transfer runs at a time.
 *
 * **Connectivity (FR-020)** is detected by the attempt rather than pre-checked. There is no
 * connectivity seam in this codebase, and adding one would mean three new platform `actual`s for a
 * signal the very next HTTP call produces anyway: a failed request maps
 * `RemoteError.Network → DeliveryFailure.NoConnectivity`, so the student sees exactly what US2
 * scenario 9 requires — the download does not complete, connectivity is named as the reason, a
 * retry is offered, and the matn still reports as not downloaded. The only difference is *when* the
 * message appears, and it is not a difference the student can perceive.
 */
@org.koin.core.annotation.Factory
class DownloadMatnUseCase(
    private val repository: DownloadedContentRepository,
    private val catalog: StudentCatalogRepository,
    private val storage: DeviceStorage,
) : UseCase<String, Unit> {

    override suspend fun invoke(params: String): Resource<Unit> {
        // (1) Duplicate request in any in-progress state is a no-op.
        val current = repository.observeAvailability(params).first()
        if (current is ContentAvailability.Downloaded ||
            current is ContentAvailability.Downloading ||
            current is ContentAvailability.Queued
        ) {
            return Resource.Success(Unit)
        }

        // (2) Free space, against the overview's always-available figure. A size of 0 means the
        // teacher published no measurement, so the guard is skipped rather than refusing outright.
        val required = catalog.overview(params)?.downloadSizeBytes ?: 0L
        val available = storage.freeSpaceBytes()
        if (required > 0L && available < required) {
            return Resource.Failure(
                DeliveryError.DeliveryFailed(
                    DeliveryFailure.InsufficientStorage(
                        requiredBytes = required,
                        availableBytes = available,
                    ),
                ),
            )
        }

        // (3) Enqueue.
        return when (val outcome = repository.download(params)) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Failure -> Resource.Failure(outcome.error)
        }
    }
}
