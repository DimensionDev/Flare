package dev.dimension.flare.web.shared

@Target(AnnotationTarget.CLASS)
public annotation class WebPresenter(
    public val name: String,
)

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class WebIgnore
