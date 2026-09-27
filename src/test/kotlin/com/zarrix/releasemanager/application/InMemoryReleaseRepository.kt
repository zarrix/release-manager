package com.zarrix.releasemanager.application

import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Deployment
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion

class InMemoryReleaseRepository : ReleaseRepository {

    private val counters = mutableMapOf<Environment, SystemVersion>()
    val ledger = mutableListOf<Deployment>()

    override fun ensureEnvironment(environment: Environment) {
        counters.putIfAbsent(environment, SystemVersion.NONE)
    }

    override fun lockCurrentVersion(environment: Environment): SystemVersion = counters.getValue(environment)

    override fun currentVersion(environment: Environment): SystemVersion? = counters[environment]

    override fun latestVersionOf(environment: Environment, serviceName: String): Int? =
        ledger.lastOrNull { it.environment == environment && it.service.name == serviceName }?.service?.version

    override fun append(deployment: Deployment) {
        ledger += deployment
    }

    override fun updateCurrentVersion(environment: Environment, systemVersion: SystemVersion) {
        counters[environment] = systemVersion
    }

    override fun snapshotAt(environment: Environment, systemVersion: SystemVersion): List<DeployedService> =
        ledger
            .filter { it.environment == environment && it.systemVersion.value <= systemVersion.value }
            .groupBy { it.service.name }
            .map { (_, rows) -> rows.last().service }
            .sortedBy { it.name }
}
