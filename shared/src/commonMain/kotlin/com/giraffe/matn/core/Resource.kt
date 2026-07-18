package com.giraffe.matn.core

sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Failure(val error: AppError) : Resource<Nothing>
}

interface AppError {
    data class Storage(val message: String) : AppError
    data object NotFound : AppError
}