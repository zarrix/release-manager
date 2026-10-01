package com.zarrix.releasemanager.repository

import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Deployment
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion

interface ReleaseRepository {

    fun ensureEnvironment(environment: Environment)

    fun lockCurrentVersion(environment: Environment): SystemVersion

    fun currentVersion(environment: Environment): SystemVersion?

    fun latestVersionOf(environment: Environment, serviceName: String): Int?

    fun append(deployment: Deployment)

    fun updateCurrentVersion(environment: Environment, systemVersion: SystemVersion)

    fun snapshotAt(environment: Environment, systemVersion: SystemVersion): List<DeployedService>
}