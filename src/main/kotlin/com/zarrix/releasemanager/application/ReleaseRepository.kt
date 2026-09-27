package com.zarrix.releasemanager.application

import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Deployment
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion

interface ReleaseRepository {

    /** Creates the environment's counter row if absent. Runs in its own transaction. */
    fun ensureEnvironment(environment: Environment)

    /** Reads the environment's counter and holds a row lock on it until the transaction ends. */
    fun lockCurrentVersion(environment: Environment): SystemVersion

    fun currentVersion(environment: Environment): SystemVersion?

    fun latestVersionOf(environment: Environment, serviceName: String): Int?

    fun append(deployment: Deployment)

    fun updateCurrentVersion(environment: Environment, systemVersion: SystemVersion)

    fun snapshotAt(environment: Environment, systemVersion: SystemVersion): List<DeployedService>
}
