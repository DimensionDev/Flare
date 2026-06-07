package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteDriver
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public expect fun createDatabaseDriver(): SQLiteDriver
