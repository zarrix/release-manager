package com.zarrix.releasemanager.service

import com.zarrix.releasemanager.PostgresTestConfiguration
import com.zarrix.releasemanager.domain.DeployedService
import com.zarrix.releasemanager.domain.Environment
import com.zarrix.releasemanager.domain.SystemVersion
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.forEach

@SpringBootTest
@Import(PostgresTestConfiguration::class)
class ConcurrentDeployTest(
    @Autowired private val releaseManager: ReleaseManagerService,
    @Autowired private val jdbc: JdbcClient,
) {

    private val parallelism = 50

    @Test
    fun `parallel deploys of different services produce a gapless sequence`() {
        val environment = freshEnvironment()

        val versions = inParallel { i -> releaseManager.deploy(environment, DeployedService("service-$i", 1)) }

        Assertions.assertThat(versions.map { it.value }).containsExactlyInAnyOrderElementsOf(1L..parallelism)
        Assertions.assertThat(releaseManager.servicesAt(environment, SystemVersion(parallelism.toLong())))
            .hasSize(parallelism)
            .extracting<String> { it.name }
            .containsExactlyInAnyOrderElementsOf((0 until parallelism).map { "service-$it" })
        assertCounterMatchesLedger(environment)
    }

    @Test
    fun `parallel deploys of the same service version bump exactly once`() {
        val environment = freshEnvironment()

        val versions = inParallel { releaseManager.deploy(environment, DeployedService("service", 1)) }

        Assertions.assertThat(versions).containsOnly(SystemVersion(1))
        Assertions.assertThat(ledgerSize(environment)).isEqualTo(1)
        assertCounterMatchesLedger(environment)
    }

    @Test
    fun `parallel first deploys to new environments each start at one`() {
        val environments = List(parallelism) { freshEnvironment() }

        val versions = inParallel { i -> releaseManager.deploy(environments[i], DeployedService("service", 1)) }

        Assertions.assertThat(versions).containsOnly(SystemVersion(1))
        environments.forEach(::assertCounterMatchesLedger)
    }

    private fun <T> inParallel(action: (Int) -> T): List<T> {
        val executor = Executors.newFixedThreadPool(parallelism)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until parallelism).map { i ->
                executor.submit<T> {
                    start.await()
                    action(i)
                }
            }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun freshEnvironment() = Environment("env-" + UUID.randomUUID())

    private fun ledgerSize(environment: Environment): Int =
        jdbc.sql("SELECT COUNT(*) FROM deployment WHERE environment = :environment")
            .param("environment", environment.name)
            .query(Int::class.java)
            .single()

    private fun assertCounterMatchesLedger(environment: Environment) {
        val counter = jdbc.sql("SELECT current_system_version FROM release_state WHERE environment = :environment")
            .param("environment", environment.name)
            .query(Long::class.java)
            .single()
        val ledgerMax = jdbc.sql("SELECT MAX(system_version) FROM deployment WHERE environment = :environment")
            .param("environment", environment.name)
            .query(Long::class.java)
            .single()
        Assertions.assertThat(counter).isEqualTo(ledgerMax)
    }
}