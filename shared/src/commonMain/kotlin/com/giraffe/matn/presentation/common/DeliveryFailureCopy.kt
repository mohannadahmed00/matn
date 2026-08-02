package com.giraffe.matn.presentation.common

import androidx.compose.runtime.Composable
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.model.DeliveryFailure
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.content_error_cancelled
import matn.shared.generated.resources.content_error_connect
import matn.shared.generated.resources.content_error_free_space
import matn.shared.generated.resources.content_error_insufficient_storage
import matn.shared.generated.resources.content_error_no_connectivity
import matn.shared.generated.resources.content_error_retry
import matn.shared.generated.resources.content_error_server
import matn.shared.generated.resources.content_error_source_unavailable
import matn.shared.generated.resources.content_error_unknown
import org.jetbrains.compose.resources.stringResource

/**
 * FR-043: every failure a student can hit surfaces as a **specific, human-readable message with a
 * clear next action** — never a raw error code and never a silent no-op.
 *
 * One mapping, in one place, exhaustive over [DeliveryFailure]. Exhaustiveness is the point: adding
 * a failure value without giving it copy becomes a compile error rather than a message that quietly
 * degrades to "something went wrong".
 *
 * [action] is deliberately separate from [message]. FR-043 asks for a cause *and* a next step, and
 * the two differ — "not enough space" pairs with "free some up", not with "try again", which would
 * fail identically the second time.
 */
data class DeliveryFailureCopy(val message: String, val action: String?)

@Composable
fun deliveryFailureCopy(failure: DeliveryFailure, requiredAvailable: String? = null): DeliveryFailureCopy =
    when (failure) {
        DeliveryFailure.NoConnectivity -> DeliveryFailureCopy(
            message = stringResource(Res.string.content_error_no_connectivity),
            action = stringResource(Res.string.content_error_connect),
        )

        // FR-019 requires the figures themselves, not just "not enough space".
        is DeliveryFailure.InsufficientStorage -> DeliveryFailureCopy(
            message = stringResource(Res.string.content_error_insufficient_storage) +
                (requiredAvailable?.let { " ($it)" } ?: ""),
            action = stringResource(Res.string.content_error_free_space),
        )

        // The student did this on purpose — state it plainly, offer no corrective action.
        DeliveryFailure.Cancelled -> DeliveryFailureCopy(
            message = stringResource(Res.string.content_error_cancelled),
            action = null,
        )

        is DeliveryFailure.Remote -> DeliveryFailureCopy(
            message = when (failure.error) {
                RemoteError.Network -> stringResource(Res.string.content_error_no_connectivity)
                RemoteError.Server -> stringResource(Res.string.content_error_server)
                else -> stringResource(Res.string.content_error_unknown)
            },
            // Honour the error's own retryability rather than always offering a retry that cannot help.
            action = if (failure.error.retryable) stringResource(Res.string.content_error_retry) else null,
        )

        // The matn stopped being published. Retrying cannot fix it, so do not offer one.
        DeliveryFailure.SourceUnavailable -> DeliveryFailureCopy(
            message = stringResource(Res.string.content_error_source_unavailable),
            action = null,
        )
    }
