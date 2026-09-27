package com.zarrix.releasemanager.api

import com.zarrix.releasemanager.application.ReleaseManagerService
import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ReleaseManagerController(private val releaseManager: ReleaseManagerService) {

    @Operation(
        summary = "Report a deployment",
        description = "Records that a service version is deployed in an environment (optional, defaults to \"default\"). " +
            "Increments the environment's SystemVersion only if the service is new or its version differs from the one " +
            "currently known; otherwise returns the current SystemVersion unchanged.",
    )
    @ApiResponse(responseCode = "200", description = "Current SystemVersion of the environment")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = [Content(schema = Schema(implementation = ProblemDetail::class))])
    @PostMapping("/deploy")
    fun deploy(@Valid @RequestBody request: DeployRequest): DeployResponse {
        val environment = Environment.of(request.environment)
        val service = DeployedService(requireNotNull(request.name).trim(), requireNotNull(request.version))
        val systemVersion = releaseManager.deploy(environment, service)
        return DeployResponse(systemVersion.value, environment.name)
    }

    @Operation(
        summary = "List services deployed under a SystemVersion",
        description = "Returns every service known in the environment as of the given SystemVersion, sorted by name.",
    )
    @ApiResponse(responseCode = "200", description = "Services and their versions at that SystemVersion")
    @ApiResponse(responseCode = "400", description = "Missing or invalid parameters", content = [Content(schema = Schema(implementation = ProblemDetail::class))])
    @ApiResponse(responseCode = "404", description = "Unknown SystemVersion or environment", content = [Content(schema = Schema(implementation = ProblemDetail::class))])
    @GetMapping("/services")
    fun services(
        @Parameter(description = "SystemVersion to look up, starting at 1 per environment", required = true)
        @RequestParam systemVersion: Long,
        @Parameter(description = "Environment name; defaults to \"default\" when omitted")
        @RequestParam(required = false) @Size(max = 100) @Pattern(regexp = ENVIRONMENT_PATTERN) environment: String?,
    ): List<ServiceResponse> =
        releaseManager.servicesAt(Environment.of(environment), SystemVersion(systemVersion))
            .map { ServiceResponse(it.name, it.version) }
}
