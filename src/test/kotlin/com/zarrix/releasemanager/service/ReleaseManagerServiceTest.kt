package com.zarrix.releasemanager.service

import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Deployment
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion
import com.zarrix.releasemanager.domain.UnknownSystemVersionException
import com.zarrix.releasemanager.repository.ReleaseRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReleaseManagerServiceTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val repository = mockk<ReleaseRepository>(relaxUnitFun = true)
    private val service = ReleaseManagerService(repository, Clock.fixed(now, ZoneOffset.UTC))
    private val prod = Environment("prod")

    @Nested
    inner class Deploy {

        private fun givenCurrentState(current: Long, latestOfServiceA: Int?) {
            every { repository.lockCurrentVersion(prod) } returns SystemVersion(current)
            every { repository.latestVersionOf(prod, "Service A") } returns latestOfServiceA
        }

        @Test
        fun `a new service bumps the system version`() {
            givenCurrentState(current = 7, latestOfServiceA = null)

            assertThat(service.deploy(prod, DeployedService("Service A", 1))).isEqualTo(SystemVersion(8))

            verify { repository.append(Deployment(prod, SystemVersion(8), DeployedService("Service A", 1), now)) }
            verify { repository.updateCurrentVersion(prod, SystemVersion(8)) }
        }

        @Test
        fun `a changed version bumps the system version`() {
            givenCurrentState(current = 7, latestOfServiceA = 1)

            assertThat(service.deploy(prod, DeployedService("Service A", 2))).isEqualTo(SystemVersion(8))

            verify { repository.append(match { it.systemVersion == SystemVersion(8) && it.service.version == 2 }) }
            verify { repository.updateCurrentVersion(prod, SystemVersion(8)) }
        }

        @Test
        fun `a rollback to a lower version is a change`() {
            givenCurrentState(current = 7, latestOfServiceA = 5)

            assertThat(service.deploy(prod, DeployedService("Service A", 4))).isEqualTo(SystemVersion(8))

            verify { repository.append(match { it.service.version == 4 }) }
        }

        @Test
        fun `an unchanged version returns the current system version and writes nothing`() {
            givenCurrentState(current = 7, latestOfServiceA = 2)

            assertThat(service.deploy(prod, DeployedService("Service A", 2))).isEqualTo(SystemVersion(7))

            verify(exactly = 0) { repository.append(any()) }
            verify(exactly = 0) { repository.updateCurrentVersion(any(), any()) }
        }

        @Test
        fun `the environment row is ensured and locked before anything is read`() {
            givenCurrentState(current = 0, latestOfServiceA = null)

            service.deploy(prod, DeployedService("Service A", 1))

            verifyOrder {
                repository.ensureEnvironment(prod)
                repository.lockCurrentVersion(prod)
                repository.latestVersionOf(prod, "Service A")
                repository.append(any())
                repository.updateCurrentVersion(prod, SystemVersion(1))
            }
        }
    }

    @Nested
    inner class ServicesAt {

        private val snapshot = listOf(DeployedService("Service A", 2), DeployedService("Service B", 1))

        @Test
        fun `returns the snapshot for a known system version`() {
            every { repository.currentVersion(prod) } returns SystemVersion(3)
            every { repository.snapshotAt(prod, SystemVersion(2)) } returns snapshot

            assertThat(service.servicesAt(prod, SystemVersion(2))).isEqualTo(snapshot)
        }

        @Test
        fun `the current system version itself is a known version`() {
            every { repository.currentVersion(prod) } returns SystemVersion(3)
            every { repository.snapshotAt(prod, SystemVersion(3)) } returns snapshot

            assertThat(service.servicesAt(prod, SystemVersion(3))).isEqualTo(snapshot)
        }

        @Test
        fun `rejects a version above the current one`() {
            every { repository.currentVersion(prod) } returns SystemVersion(3)

            assertThatThrownBy { service.servicesAt(prod, SystemVersion(4)) }
                .isInstanceOf(UnknownSystemVersionException::class.java)
            verify(exactly = 0) { repository.snapshotAt(any(), any()) }
        }

        @Test
        fun `rejects version zero`() {
            every { repository.currentVersion(prod) } returns SystemVersion(3)

            assertThatThrownBy { service.servicesAt(prod, SystemVersion(0)) }
                .isInstanceOf(UnknownSystemVersionException::class.java)
        }

        @Test
        fun `rejects an environment that has never had a deploy`() {
            every { repository.currentVersion(prod) } returns null

            assertThatThrownBy { service.servicesAt(prod, SystemVersion(1)) }
                .isInstanceOf(UnknownSystemVersionException::class.java)
                .hasMessage("System version 1 does not exist in environment 'prod'")
        }
    }
}
