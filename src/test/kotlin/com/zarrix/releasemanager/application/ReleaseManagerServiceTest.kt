package com.zarrix.releasemanager.application

import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion
import com.zarrix.releasemanager.domain.UnknownSystemVersionException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReleaseManagerServiceTest {

    private val repository = InMemoryReleaseRepository()
    private val service = ReleaseManagerService(
        repository,
        TransactionOperations.withoutTransaction(),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
    )
    private val prod = Environment("prod")
    private val staging = Environment("staging")

    @Test
    fun `a new service bumps the system version`() {
        assertThat(service.deploy(prod, DeployedService("Service A", 1))).isEqualTo(SystemVersion(1))
        assertThat(service.deploy(prod, DeployedService("Service B", 1))).isEqualTo(SystemVersion(2))
    }

    @Test
    fun `a changed version bumps the system version`() {
        service.deploy(prod, DeployedService("Service A", 1))

        assertThat(service.deploy(prod, DeployedService("Service A", 2))).isEqualTo(SystemVersion(2))
    }

    @Test
    fun `an unchanged version returns the current system version without writing`() {
        service.deploy(prod, DeployedService("Service A", 1))
        service.deploy(prod, DeployedService("Service B", 1))

        assertThat(service.deploy(prod, DeployedService("Service A", 1))).isEqualTo(SystemVersion(2))
        assertThat(repository.ledger).hasSize(2)
    }

    @Test
    fun `a rollback to a lower version is a change`() {
        service.deploy(prod, DeployedService("Service A", 5))

        assertThat(service.deploy(prod, DeployedService("Service A", 4))).isEqualTo(SystemVersion(2))
    }

    @Test
    fun `a snapshot contains every service as of that version`() {
        service.deploy(prod, DeployedService("Service A", 1))
        service.deploy(prod, DeployedService("Service B", 1))
        service.deploy(prod, DeployedService("Service A", 2))

        assertThat(service.servicesAt(prod, SystemVersion(1)))
            .containsExactly(DeployedService("Service A", 1))
        assertThat(service.servicesAt(prod, SystemVersion(2)))
            .containsExactly(DeployedService("Service A", 1), DeployedService("Service B", 1))
        assertThat(service.servicesAt(prod, SystemVersion(3)))
            .containsExactly(DeployedService("Service A", 2), DeployedService("Service B", 1))
    }

    @Test
    fun `unknown system versions are rejected`() {
        service.deploy(prod, DeployedService("Service A", 1))

        assertThatThrownBy { service.servicesAt(prod, SystemVersion(0)) }
            .isInstanceOf(UnknownSystemVersionException::class.java)
        assertThatThrownBy { service.servicesAt(prod, SystemVersion(2)) }
            .isInstanceOf(UnknownSystemVersionException::class.java)
        assertThatThrownBy { service.servicesAt(Environment("never-seen"), SystemVersion(1)) }
            .isInstanceOf(UnknownSystemVersionException::class.java)
    }

    @Test
    fun `environments have independent system version sequences`() {
        service.deploy(prod, DeployedService("Service A", 1))
        service.deploy(prod, DeployedService("Service B", 1))

        assertThat(service.deploy(staging, DeployedService("Service C", 7))).isEqualTo(SystemVersion(1))
        assertThat(service.deploy(prod, DeployedService("Service B", 1))).isEqualTo(SystemVersion(2))
        assertThat(service.servicesAt(staging, SystemVersion(1))).containsExactly(DeployedService("Service C", 7))
        assertThat(service.servicesAt(prod, SystemVersion(2)))
            .containsExactly(DeployedService("Service A", 1), DeployedService("Service B", 1))
        assertThatThrownBy { service.servicesAt(staging, SystemVersion(2)) }
            .isInstanceOf(UnknownSystemVersionException::class.java)
    }
}
