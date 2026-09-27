package com.zarrix.releasemanager.domain

class UnknownSystemVersionException(environment: Environment, systemVersion: SystemVersion) :
    RuntimeException("System version ${systemVersion.value} does not exist in environment '${environment.name}'")
