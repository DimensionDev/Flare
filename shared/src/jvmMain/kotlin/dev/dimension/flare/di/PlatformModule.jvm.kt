package dev.dimension.flare.di

import dev.dimension.flare.common.FileSystemUtilsExt
import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datastore.AppDataStore
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.io.File

internal actual val platformModule: Module =
    module {
        single {
            AppDataStore {
                File(FileSystemUtilsExt.flareDirectory(), it).absolutePath
            }
        }
        singleOf(::DriverFactory)
    }

public object KoinHelper {
    public fun modules(): List<Module> = appModule()
}
