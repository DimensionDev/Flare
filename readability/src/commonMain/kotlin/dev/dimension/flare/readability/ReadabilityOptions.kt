package dev.dimension.flare.readability

/**
 * Configuration options for [Readability].
 */
public data class ReadabilityOptions(
    /**
     * Enable debug logging.
     */
    public val debug: Boolean = false,
    /**
     * Max number of elements to parse. 0 means no limit.
     */
    public val maxElemsToParse: Int = DEFAULT_MAX_ELEMS_TO_PARSE,
    /**
     * The number of top candidates to consider when analysing how
     * tight the competition is among candidates.
     */
    public val nbTopCandidates: Int = DEFAULT_N_TOP_CANDIDATES,
    /**
     * The minimum number of characters an article must have in order to return a result.
     */
    public val charThreshold: Int = DEFAULT_CHAR_THRESHOLD,
    /**
     * Additional classes to preserve on elements.
     */
    public val classesToPreserve: List<String> = emptyList(),
    /**
     * Whether to keep all classes on elements.
     */
    public val keepClasses: Boolean = false,
    /**
     * Custom serializer for the article content element.
     * By default uses element's inner HTML.
     */
    public val serializer: ((com.fleeksoft.ksoup.nodes.Element) -> String)? = null,
    /**
     * Disable JSON-LD metadata extraction.
     */
    public val disableJSONLD: Boolean = false,
    /**
     * Custom regex for allowed video sources.
     */
    public val allowedVideoRegex: Regex? = null,
    /**
     * Modifier for link density threshold.
     */
    public val linkDensityModifier: Double = 0.0,
) {
    public companion object {
        public const val DEFAULT_MAX_ELEMS_TO_PARSE: Int = 0
        public const val DEFAULT_N_TOP_CANDIDATES: Int = 5
        public const val DEFAULT_CHAR_THRESHOLD: Int = 500
    }
}
