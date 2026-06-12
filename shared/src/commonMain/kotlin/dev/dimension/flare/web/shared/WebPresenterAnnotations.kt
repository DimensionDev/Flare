package dev.dimension.flare.web.shared

@Target(AnnotationTarget.CLASS)
public annotation class WebPresenter(
    public val name: String,
    public val creatable: Boolean = true,
)

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class WebIgnore
