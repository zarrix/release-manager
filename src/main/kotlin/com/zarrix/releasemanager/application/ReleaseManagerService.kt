package com.zarrix.releasemanager.application

import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Deployment
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion
import com.zarrix.releasemanager.domain.UnknownSystemVersionException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock

@Service
class ReleaseManagerService(
    private val repository: ReleaseRepository,
    private val transaction: TransactionOperations,
    private val clock: Clock,
) {

    fun deploy(environment: Environment, service: DeployedService): SystemVersion {
        repository.ensureEnvironment(environment)
        return checkNotNull(transaction.execute { recordIfChanged(environment, service) })
    }

    private fun recordIfChanged(environment: Environment, service: DeployedService): SystemVersion {
        val current = repository.lockCurrentVersion(environment)
        if (repository.latestVersionOf(environment, service.name) == service.version) {
            return current
        }
        val next = current.next()
        repository.append(Deployment(environment, next, service, clock.instant()))
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
