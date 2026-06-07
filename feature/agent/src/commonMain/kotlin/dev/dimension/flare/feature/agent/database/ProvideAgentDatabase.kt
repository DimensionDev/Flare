package dev.dimension.flare.feature.agent.database

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.database.createDatabaseDriver
import org.koin.core.annotation.Single

private const val AGENT_DATABASE_NAME = "agent.db"

@Single
internal fun provideAgentDatabase(driverFactory: DriverFactory): AgentDatabase =
    driverFactory
        .createBuilder<AgentDatabase>(AGENT_DATABASE_NAME)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(createDatabaseDriver())
        .setQueryCoroutineContext(PlatformDispatchers.IO)
        .build()
