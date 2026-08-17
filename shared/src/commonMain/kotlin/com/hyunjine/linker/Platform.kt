package com.hyunjine.linker

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform