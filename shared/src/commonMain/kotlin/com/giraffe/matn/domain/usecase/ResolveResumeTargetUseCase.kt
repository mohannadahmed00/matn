package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.ResumeTarget
import com.giraffe.matn.domain.repository.SessionStateRepository
import com.giraffe.matn.domain.repository.VerseRepository
import com.giraffe.matn.domain.session.ResumeTargetResolver
import com.giraffe.matn.domain.session.VerseRef

/** Resolves what tapping Continue Learning for a matn should do (contracts §5). Fetches the saved
 *  session and the matn's current ordered verses, then delegates every branching decision to the
 *  pure [ResumeTargetResolver] — this use case contains no `if` about missing verses. */
class ResolveResumeTargetUseCase(
    private val sessionState: SessionStateRepository,
    private val verses: VerseRepository,
) : UseCase<String, ResumeTarget> {

    override suspend fun invoke(params: String): Resource<ResumeTarget> {
        val session = sessionState.getSession(params)
        val verseList = when (val result = verses.getVersesByMatn(params)) {
            is Resource.Success -> result.data
            is Resource.Failure -> return result
        }
        val ordered = verseList.map { VerseRef(it.id, it.displayNumber) }
        val target = ResumeTargetResolver.resolve(session, ordered)
        return Resource.Success(target)
    }
}