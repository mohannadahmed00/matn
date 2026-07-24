package com.giraffe.matn.domain.model

/** Today's daily-goal progress (data-model.md §2.2, FR-012). */
data class DailyProgress(
    val practicedToday: Int,
    val goal: Int,
) {
    val fraction: Float get() = if (goal <= 0) 0f else (practicedToday.toFloat() / goal).coerceIn(0f, 1f)
    val isComplete: Boolean get() = goal > 0 && practicedToday >= goal
}
