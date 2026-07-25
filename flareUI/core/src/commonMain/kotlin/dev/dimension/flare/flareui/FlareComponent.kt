package dev.dimension.flare.flareui

/**
 * Marks one [WidgetType] as part of the generated Flare UI component vocabulary.
 *
 * The code generator discovers the props type from [WidgetType]'s generic argument.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class FlareComponent
