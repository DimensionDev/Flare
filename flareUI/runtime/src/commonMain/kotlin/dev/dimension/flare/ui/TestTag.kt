package dev.dimension.flare.ui

import androidx.compose.runtime.Immutable

@Immutable
public data class TestTagElement(
    public val value: String,
) : FlareModifier.Element {
    init {
        require(value.isNotBlank()) { "A test tag cannot be blank." }
    }
}

public fun FlareModifier.testTag(value: String): FlareModifier = this then TestTagElement(value)

public fun FlareModifier.testTagOrNull(): String? = lastElementOfType<TestTagElement>()?.value
