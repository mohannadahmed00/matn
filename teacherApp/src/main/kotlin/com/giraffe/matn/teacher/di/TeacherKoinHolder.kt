package com.giraffe.matn.teacher.di

import org.koin.core.Koin

/** Holds the started [Koin] instance for `:teacherApp`. Mirrors `com.giraffe.matn.di.MatnKoinHolder`. */
object TeacherKoinHolder {
    private var _koin: Koin? = null

    val koin: Koin
        get() = _koin ?: error("TeacherKoinHolder not initialized — call startTeacherKoin first")

    fun initialize(koin: Koin) {
        _koin = koin
    }
}
