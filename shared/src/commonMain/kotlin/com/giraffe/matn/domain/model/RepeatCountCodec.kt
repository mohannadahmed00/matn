package com.giraffe.matn.domain.model

private const val UNLIMITED_TOKEN = "UNLIMITED"

/** FR-007: unlimited stays distinguishable from every finite value and from unset. */
fun RepeatCount.encode(): String = when (this) {
    is RepeatCount.Unlimited -> UNLIMITED_TOKEN
    is RepeatCount.Finite -> value.toString()
}

/** Total decode — never throws; unparseable/absent input falls back to [RepeatCount.ONE]. */
fun decodeRepeatCount(raw: String?): RepeatCount = when {
    raw == null -> RepeatCount.ONE
    raw == UNLIMITED_TOKEN -> RepeatCount.Unlimited
    else -> raw.toIntOrNull()?.let { RepeatCount.of(it) } ?: RepeatCount.ONE
}