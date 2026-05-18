package dev.dimension.flare.common

import nl.adaptivity.xmlutil.serialization.XML

public val Xml: XML =
    XML {
        defaultPolicy {
            autoPolymorphic = true
            ignoreUnknownChildren()
        }
        defaultToGenericParser = true
    }
