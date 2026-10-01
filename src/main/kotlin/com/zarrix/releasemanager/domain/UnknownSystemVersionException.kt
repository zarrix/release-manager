package com.zarrix.releasemanager.domain

class UnknownSystemVersionException(val environment: Environment, val systemVersion: SystemVersion) :
    RuntimeException("System version ${systemVersion.value} does not exist in environment '${environment.name}'")
