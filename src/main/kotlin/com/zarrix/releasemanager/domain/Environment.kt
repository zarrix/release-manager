package com.zarrix.releasemanager.domain

@JvmInline
value class Environment(val name: String) {

    companion object {
        val DEFAULT = Environment("default")

        fun of(name: String?): Environment =
            name?.trim()?.takeIf { it.isNotEmpty() }?.let(::Environment) ?: DEFAULT
    }
}
