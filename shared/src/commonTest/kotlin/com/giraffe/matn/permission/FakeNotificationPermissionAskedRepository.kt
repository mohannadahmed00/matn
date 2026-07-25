package com.giraffe.matn.permission

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.repository.NotificationPermissionAskedRepository

/** In-memory fake [NotificationPermissionAskedRepository] for tests (T080/T081). */
class FakeNotificationPermissionAskedRepository(initial: Boolean = false) : NotificationPermissionAskedRepository {
    var asked: Boolean = initial
        private set

    override fun askedNow(): Boolean = asked

    override suspend fun markAsked(): Resource<Unit> {
        asked = true
        return Resource.Success(Unit)
    }
}
