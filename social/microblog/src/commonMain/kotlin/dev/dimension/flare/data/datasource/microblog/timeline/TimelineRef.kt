package dev.dimension.flare.data.datasource.microblog.timeline

public data class TimelineRef<T : TimelineSpec.Data>(
    public val spec: TimelineSpec<T>,
    public val data: T,
) {
    public val semanticId: String
        get() = "${spec.id}:${spec.stableKey(data)}"
}
