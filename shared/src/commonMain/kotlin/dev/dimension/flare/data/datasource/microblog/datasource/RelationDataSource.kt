package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface RelationDataSource {
    public val relationHandler: RelationHandler
    public val supportedRelationTypes: Set<RelationActionType>
}
