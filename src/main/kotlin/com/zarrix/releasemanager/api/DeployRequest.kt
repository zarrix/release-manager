package com.zarrix.releasemanager.api

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

const val ENVIRONMENT_PATTERN = "\\s*[A-Za-z0-9._-]+\\s*"

data class DeployRequest(
    @field:NotBlank
    @field:Schema(description = "Service name, case-sensitive", example = "Service A")
    val name: String?,

    @field:NotNull
    @field:Min(1)
    @field:Schema(description = "Deployed version of the service", example = "1")
    val version: Int?,

    @field:Size(max = 100)
    @field:Pattern(regexp = ENVIRONMENT_PATTERN)
    @field:Schema(description = "Optional environment; defaults to \"default\"", example = "prod", nullable = true)
    val environment: String? = null,
)
