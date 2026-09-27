package com.zarrix.releasemanager.domain

import java.time.Instant

data class Deployment(
    val environment: Environment,
    val systemVersion: SystemVersion,
    val service: DeployedService,
    val deployedAt: Instant,
)
