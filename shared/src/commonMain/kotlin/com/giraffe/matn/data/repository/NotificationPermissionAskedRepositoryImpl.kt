package com.giraffe.matn.data.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.repository.NotificationPermissionAskedRepository

private const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
private const val VALUE_TRUE = "true"

/** Implements [NotificationPermissionAskedRepository] over `app_setting` (data-model §1). */
class NotificationPermissionAskedRepositoryImpl(
    private val db: ContentDatabase,
) : NotificationPermissionAskedRepository {

    override fun askedNow(): Boolean =
        db.contentQueries.selectSetting(KEY_NOTIFICATION_PERMISSION_ASKED).executeAsOneOrNull() == VALUE_TRUE

    override suspend fun markAsked(): Resource<Unit> =
        storageCall({ "Failed to persist notification-permission-asked flag" }) {
            db.contentQueries.upsertSetting(KEY_NOTIFICATION_PERMISSION_ASKED, VALUE_TRUE)
        }
}
