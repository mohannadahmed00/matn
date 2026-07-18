package com.giraffe.matn

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform