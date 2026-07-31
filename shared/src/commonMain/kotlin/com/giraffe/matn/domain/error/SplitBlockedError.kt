package com.giraffe.matn.domain.error

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.audio.SplitProblem

/** `ApplySplitUseCase` refuses before slicing when any rule R1–R6 is violated (FR-015, FR-016) —
 * this never reaches the repository, so a blocked split costs nothing. */
data class SplitBlockedError(val problems: List<SplitProblem>) : AppError
