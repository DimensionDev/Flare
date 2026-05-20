package dev.dimension.flare.data.datasource.microblog.timeline

public class TimelineCatalog(
    specs: Iterable<TimelineSpec<out TimelineSpec.Data>>,
) {
    private val specsById: Map<String, TimelineSpec<out TimelineSpec.Data>> =
        specs
            .distinctBy { it.id }
            .associateBy { it.id }

    public val specs: List<TimelineSpec<out TimelineSpec.Data>>
        get() = specsById.values.toList()

    public fun requireSpec(id: String): TimelineSpec<out TimelineSpec.Data> =
        requireNotNull(specsById[id]) {
            "No timeline spec registered for $id"
        }

    public fun encode(ref: TimelineRef<out TimelineSpec.Data>): EncodedTimelineRef = encodeTyped(ref)

    public fun decode(
        specId: String,
        encodedData: String,
    ): TimelineRef<out TimelineSpec.Data> = decodeTyped(requireSpec(specId), encodedData)

    private fun <T : TimelineSpec.Data> encodeTyped(ref: TimelineRef<T>): EncodedTimelineRef =
        EncodedTimelineRef(
            specId = ref.spec.id,
            stableKey = ref.spec.stableKey(ref.data),
            data = ref.spec.encode(ref.data),
        )

    @Suppress("UNCHECKED_CAST")
    private fun decodeTyped(
        spec: TimelineSpec<out TimelineSpec.Data>,
        encodedData: String,
    ): TimelineRef<out TimelineSpec.Data> {
        val typedSpec = spec as TimelineSpec<TimelineSpec.Data>
        return typedSpec.ref(typedSpec.decode(encodedData))
    }
}

public data class EncodedTimelineRef(
    public val specId: String,
    public val stableKey: String,
    public val data: String,
)
