package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler

internal interface RelationDataSource {
    val relationHandler: RelationHandler
}
