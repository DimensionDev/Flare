package dev.dimension.flare.common

import nl.adaptivity.xmlutil.serialization.XML

internal val Xml =
    XML {
        defaultPolicy {
            autoPolymorphic = true
            ignoreUnknownChildren()
        }
        defaultToGenericParser = true
    }
