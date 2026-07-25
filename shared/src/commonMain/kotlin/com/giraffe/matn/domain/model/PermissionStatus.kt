package com.giraffe.matn.domain.model

/**
 * The current OS-level status of a runtime permission (Phase 9, data-model §2.4). Reported by the
 * [com.giraffe.matn.domain.permission.NotificationPermission] platform seam. `PERMANENTLY_DENIED`
 * is what FR-023 keys the "go to device settings" affordance off, since the OS will no longer
 * surface its own prompt.
 */
enum class PermissionStatus {
    NOT_DETERMINED,
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
}