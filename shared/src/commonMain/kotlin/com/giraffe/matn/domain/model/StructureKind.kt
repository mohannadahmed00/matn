package com.giraffe.matn.domain.model

enum class StructureKind {
    SIMPLE,
    STRUCTURED;

    companion object {
        /** Lenient parse for values read back from storage; `null` if unrecognized. */
        fun fromStorageOrNull(value: String): StructureKind? =
            entries.firstOrNull { it.name == value }
    }
}
