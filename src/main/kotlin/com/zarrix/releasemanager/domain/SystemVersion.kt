package com.zarrix.releasemanager.domain

@JvmInline
value class SystemVersion(val value: Long) {

    fun next(): SystemVersion = SystemVersion(value + 1)

    companion object {
        val NONE = SystemVersion(0)
    }
}
