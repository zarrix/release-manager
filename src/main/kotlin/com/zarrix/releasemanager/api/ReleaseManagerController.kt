package com.zarrix.releasemanager.api

import com.zarrix.releasemanager.application.ReleaseManagerService
import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ReleaseManagerController(private val releaseManager: ReleaseManagerService) {

    @PostMapping("/deploy")
    fun deploy(@Valid @RequestBody request: DeployRequest): DeployResponse {
        val environment = Environment.of(request.environment)
        val service = DeployedService(requireNotNull(request.name).trim(), requireNotNull(request.version))
        val systemVersion = releaseManager.deploy(environment, service)
        return DeployResponse(systemVersion.value, environment.name)
    }

    @GetMapping("/services")
    fun services(
        @RequestParam systemVersion: Long,
        @RequestParam(required = false) @Size(max = 100) @Pattern(regexp = ENVIRONMENT_PATTERN) environment: String?,
    ): List<ServiceResponse> =
        releaseManager.servicesAt(Environment.of(environment), SystemVersion(systemVersion))
            .map { ServiceResponse(it.name, it.version) }
}
