package com.zarrix.releasemanager.service

import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Deployment
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion
import com.zarrix.releasemanager.domain.UnknownSystemVersionException
import com.zarrix.releasemanager.repository.ReleaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class ReleaseManagerService(
    private val repository: ReleaseRepository,
    private val clock: Clock,
) {

    @Transactional
    fun deploy(environment: Environment, service: DeployedService): SystemVersion {
        repository.ensureEnvironment(environment)
        val current = repository.lockCurrentVersion(environment)
        val latest = repository.latestVersionOf(environment, service.name)
        if (latest == service.version) {
            return current
        }
        val next = current.next()
        repository.append(
            Deployment(
                environment = environment,
                systemVersion = next,
                service = service,
                deployedAt = clock.instant(),
            ),
        )
        repository.updateCurrentVersion(environment, next)
        return next
    }


    fun servicesAt(environment: Environment, systemVersion: SystemVersion): List<DeployedService> {
        val current = repository.currentVersion(environment)
        if (current == null || systemVersion.value < 1 || systemVersion.value > current.value) {
            throw UnknownSystemVersionException(environment, systemVersion)
        }
        return repository.snapshotAt(environment, systemVersion)
    }
}
