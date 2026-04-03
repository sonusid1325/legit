package com.sonusid.legit.plugins

import io.ktor.server.application.*

fun Application.configOrEnv(path: String, envName: String, default: String): String {
    return System.getenv(envName)
        ?.takeIf { it.isNotBlank() }
        ?: environment.config.propertyOrNull(path)?.getString()
        ?: default
}

fun Application.configOrEnv(path: String, envName: String): String? {
    return System.getenv(envName)
        ?.takeIf { it.isNotBlank() }
        ?: environment.config.propertyOrNull(path)?.getString()
}
