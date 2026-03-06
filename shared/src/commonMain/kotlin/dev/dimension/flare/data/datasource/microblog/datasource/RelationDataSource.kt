package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType

internal interface RelationDataSource {
    val relationHandler: RelationHandler
    val supportedRelationTypes: Set<RelationActionType>
}
