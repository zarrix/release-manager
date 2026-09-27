package com.zarrix.releasemanager.persistence

import com.zarrix.releasemanager.application.ReleaseRepository
import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Deployment
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

@Repository
class JdbcReleaseRepository(private val jdbc: JdbcClient) : ReleaseRepository {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun ensureEnvironment(environment: Environment) {
        if (currentVersion(environment) != null) return
        try {
            jdbc.sql("INSERT INTO release_state (environment, current_system_version) VALUES (:environment, 0)")
                .param("environment", environment.name)
                .update()
        } catch (_: DuplicateKeyException) {
            // another deploy created the row first; the lock below serializes the rest
        }
    }

    override fun lockCurrentVersion(environment: Environment): SystemVersion =
        jdbc.sql("SELECT current_system_version FROM release_state WHERE environment = :environment FOR UPDATE")
            .param("environment", environment.name)
            .query(Long::class.java)
            .single()
            .let(::SystemVersion)

    override fun currentVersion(environment: Environment): SystemVersion? =
        jdbc.sql("SELECT current_system_version FROM release_state WHERE environment = :environment")
            .param("environment", environment.name)
            .query(Long::class.java)
            .optional()
            .map(::SystemVersion)
            .orElse(null)

    override fun latestVersionOf(environment: Environment, serviceName: String): Int? =
        jdbc.sql(
            """
            SELECT service_version FROM deployment
            WHERE environment = :environment AND service_name = :serviceName
            ORDER BY system_version DESC
            LIMIT 1
            """,
        )
            .param("environment", environment.name)
            .param("serviceName", serviceName)
            .query(Int::class.java)
            .optional()
            .orElse(null)

    override fun append(deployment: Deployment) {
        jdbc.sql(
            """
            INSERT INTO deployment (environment, system_version, service_name, service_version, deployed_at)
            VALUES (:environment, :systemVersion, :serviceName, :serviceVersion, :deployedAt)
            """,
        )
            .param("environment", deployment.environment.name)
            .param("systemVersion", deployment.systemVersion.value)
            .param("serviceName", deployment.service.name)
            .param("serviceVersion", deployment.service.version)
            .param("deployedAt", Timestamp.from(deployment.deployedAt))
            .update()
    }

    override fun updateCurrentVersion(environment: Environment, systemVersion: SystemVersion) {
        jdbc.sql("UPDATE release_state SET current_system_version = :systemVersion WHERE environment = :environment")
            .param("systemVersion", systemVersion.value)
            .param("environment", environment.name)
            .update()
    }

    override fun snapshotAt(environment: Environment, systemVersion: SystemVersion): List<DeployedService> =
        jdbc.sql(
            """
            SELECT service_name, service_version FROM (
                SELECT service_name, service_version,
                       ROW_NUMBER() OVER (PARTITION BY service_name ORDER BY system_version DESC) AS recency
                FROM deployment
                WHERE environment = :environment AND system_version <= :systemVersion
            ) latest
            WHERE recency = 1
            ORDER BY service_name
            """,
        )
            .param("environment", environment.name)
            .param("systemVersion", systemVersion.value)
            .query { rs, _ -> DeployedService(rs.getString("service_name"), rs.getInt("service_version")) }
            .list()
}
