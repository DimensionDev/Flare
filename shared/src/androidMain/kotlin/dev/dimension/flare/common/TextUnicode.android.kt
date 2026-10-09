package dev.dimension.flare.common

import java.text.Normalizer

public actual fun String.normalizeNfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)
