package com.zarrix.releasemanager.api

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

const val ENVIRONMENT_PATTERN = "\\s*[A-Za-z0-9._-]+\\s*"

data class DeployRequest(
    @field:NotBlank
    val name: String?,

    @field:NotNull
    @field:Min(1)
    val version: Int?,

    @field:Size(max = 100)
    @field:Pattern(regexp = ENVIRONMENT_PATTERN)
    val environment: String? = null,
)
